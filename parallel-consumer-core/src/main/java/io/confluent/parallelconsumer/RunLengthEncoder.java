package io.confluent.parallelconsumer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.confluent.csid.utils.StringUtils.msg;
import static io.confluent.parallelconsumer.OffsetEncoding.RunLength;
import static io.confluent.parallelconsumer.OffsetEncoding.RunLengthCompressed;

class RunLengthEncoder extends OffsetEncoder {

    private final AtomicInteger currentRunLengthCount;
    private final AtomicBoolean previousRunLengthState;
    private final List<Integer> runLengthEncodingIntegers;

    private Optional<byte[]> encodedBytes = Optional.empty();

    public RunLengthEncoder(OffsetSimultaneousEncoder offsetSimultaneousEncoder) {
        super(offsetSimultaneousEncoder);
        // run length setup
        currentRunLengthCount = new AtomicInteger();
        previousRunLengthState = new AtomicBoolean(false);
        runLengthEncodingIntegers = new ArrayList<>();
    }

    @Override
    protected OffsetEncoding getEncodingType() {
        return RunLength;
    }

    @Override
    protected OffsetEncoding getEncodingTypeCompressed() {
        return RunLengthCompressed;
    }

    @Override
    public void encodeIncompleteOffset(final int rangeIndex) {
        encodeRunLength(false);
    }

    @Override
    public void encodeCompletedOffset(final int rangeIndex) {
        encodeRunLength(true);
    }

    @Override
    public byte[] serialise() throws EncodingNotSupportedException {
        runLengthEncodingIntegers.add(currentRunLengthCount.get()); // add tail

        ByteBuffer runLengthEncodedByteBuffer = ByteBuffer.allocate(runLengthEncodingIntegers.size() * Short.BYTES);
        for (final Integer runlength : runLengthEncodingIntegers) {
            final short shortCastRunlength = runlength.shortValue();
            if (runlength != shortCastRunlength)
                throw new RunlengthV1EncodingNotSupported(msg("Runlength too long for Short ({} cast to {})", runlength, shortCastRunlength));
            runLengthEncodedByteBuffer.putShort(shortCastRunlength);
        }

        byte[] array = runLengthEncodedByteBuffer.array();
        encodedBytes = Optional.of(array);
        return array;
    }

    @Override
    public int getEncodedSize() {
        return encodedBytes.get().length;
    }

    @Override
    protected byte[] getEncodedBytes() {
        return encodedBytes.get();
    }

    private void encodeRunLength(final boolean currentIsComplete) {
        // run length
        boolean currentOffsetMatchesOurRunLengthState = previousRunLengthState.get() == currentIsComplete;
        if (currentOffsetMatchesOurRunLengthState) {
            currentRunLengthCount.getAndIncrement();
        } else {
            previousRunLengthState.set(currentIsComplete);
            runLengthEncodingIntegers.add(currentRunLengthCount.get());
            currentRunLengthCount.set(1); // reset to 1
        }
    }
}