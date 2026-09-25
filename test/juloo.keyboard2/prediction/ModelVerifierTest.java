package juloo.keyboard2.prediction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelVerifierTest
{
  private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
  private void copy(String data, long size, String hash, AtomicBoolean cancel) throws IOException
  {
    ModelVerifier.copy(new ByteArrayInputStream(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        new ByteArrayOutputStream(), size, hash, cancel, n -> {});
  }
  @Test public void acceptsExactSizeAndChecksum() throws Exception { copy("abc", 3, ABC, new AtomicBoolean()); }
  @Test public void rejectsTruncatedOversizeAndCorruptFiles() throws Exception
  {
    for (String data : new String[]{"ab", "abcd", "abd"})
    {
      try { copy(data, 3, ABC, new AtomicBoolean()); fail(data); }
      catch (IOException expected) {}
    }
  }
  @Test public void cancellationCannotInstallEvenACompleteFile() throws Exception
  {
    AtomicBoolean cancel = new AtomicBoolean();
    try
    {
      ModelVerifier.copy(new ByteArrayInputStream(new byte[]{97, 98, 99}),
          new ByteArrayOutputStream(), 3, ABC, cancel, n -> cancel.set(true));
      fail("Cancellation must win over successful verification");
    }
    catch (IOException expected) {}
  }
}
