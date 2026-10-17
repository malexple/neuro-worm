package ru.mcs.neuroworm.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface SnapshotCodec {
    void write(NetworkSnapshot snapshot, OutputStream out) throws IOException;

    NetworkSnapshot read(InputStream in) throws IOException;
}