import java.io.Serializable;

/**
 * Chunk: Contains the file chunk with metadata.
 */
public class Chunk implements Serializable
{
	public byte[] buffer;
	public int size;
	public int offset;
	public Chunk()
	{
		offset = 0;
		size = 0 ;
	}
}