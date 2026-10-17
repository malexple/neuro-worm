package ru.mcs.neuroworm.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.mcs.neuroworm.core.NetworkSnapshot;

class JsonSnapshotCodecTest {

    @Test
    void roundTripKeepsEveryValueExactly() throws IOException {
        NetworkSnapshot s = new NetworkSnapshot(NetworkSnapshot.CURRENT_VERSION,
                List.of(new NetworkSnapshot.NeuronState("AWAL", 0.1234567890123456)),
                List.of(new NetworkSnapshot.SynapseScale("AWAL", "AIYL", 6.488)));
        JsonSnapshotCodec codec = new JsonSnapshotCodec();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.write(s, out);
        NetworkSnapshot back = codec.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(s, back);
    }
}