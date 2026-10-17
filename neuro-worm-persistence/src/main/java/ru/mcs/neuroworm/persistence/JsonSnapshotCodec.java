package ru.mcs.neuroworm.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import ru.mcs.neuroworm.core.NetworkSnapshot;
import ru.mcs.neuroworm.core.SnapshotCodec;

public final class JsonSnapshotCodec implements SnapshotCodec {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void write(NetworkSnapshot snapshot, OutputStream out) throws IOException {
        mapper.writeValue(out, snapshot);
    }

    @Override
    public NetworkSnapshot read(InputStream in) throws IOException {
        return mapper.readValue(in, NetworkSnapshot.class);
    }
}