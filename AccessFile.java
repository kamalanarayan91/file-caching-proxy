import java.io.RandomAccessFile;
/**
 * Contains the random Access File object as well as other metadata
 * about the file in use.
 */
public class AccessFile
{
	public String inputPath;
	public String cachePath;
	public String permissions;// read or write
	public RandomAccessFile rAFile;
	public boolean isModified;
		
	/**
	 * Constructor
	 */
	public AccessFile(String fPath,RandomAccessFile rAccessFile,String fPermissions)
	{
		this.inputPath = fPath;
		this.rAFile = rAccessFile;
		this.permissions = fPermissions;
		this.isModified = false;
	}

	/**
	 * update the cachePath
	 */
	public void putCachePath(String path)
	{
		this.cachePath = path;
	}


}