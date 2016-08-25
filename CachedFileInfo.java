import java.io.Serializable;
/**
 * This class is used for transferring the file data from
 * server to file.
 */
public class CachedFileInfo implements Serializable
{

	public String path;
	public String cachePath;
	public String normalizedInputPath;

	public int errorCode ;
	public int versionNumber;
	public int readerCount;
	

	public long fileSize ;
	public long lastModifiedTime;
	

	public boolean isDir;
	public boolean isModified;
	public boolean isInUse;
	public boolean isReadOnly;
	
	/**
	 * Constructor
	 */
	public CachedFileInfo()
	{
		path = null;
		cachePath = null;
		errorCode  = 0;

		fileSize   =  -1;
		lastModifiedTime =-1;

		isDir = false;
		isModified = false;

		versionNumber = 1;
		readerCount = 0;

		isInUse = false;
		isReadOnly = false;

		normalizedInputPath = null;
	}

	/**
	 * Copy constructor
	 * @param  other object to copy from.
	 * @return       
	 */
	public CachedFileInfo(CachedFileInfo other)
	{
		this.path = other.path;
		this.cachePath =other.cachePath;

		this.errorCode = other.errorCode;
		this.versionNumber=other.versionNumber;
		this.readerCount=other.readerCount;


		this.fileSize =other.fileSize;
		this.lastModifiedTime=other.lastModifiedTime;
		

		this.isDir=other.isDir;
		this.isModified=other.isModified;
		this.isInUse=other.isInUse;
		this.isReadOnly = other.isReadOnly;	
		this.normalizedInputPath = other.normalizedInputPath;
	}


	/*Put methods*/
	public void incrReaderCount()
	{
		this.readerCount++;
	}

	public void decrReaderCount()
	{
		this.readerCount--;
	}

	public void incrVersionNumber()
	{
		versionNumber++;
	}

	public void putVersionNumber(int ver)
	{
		versionNumber = ver;
	}

	public void putIsDir(boolean val)
	{
		isDir = val;
	}

	public void putLastModifiedTime(long val)
	{
		lastModifiedTime = val;
	}

	public void putFileSize(long size)
	{
		fileSize = size;
	}


	public void putErrorCode(int code)
	{
		errorCode = code;
	}

	public void putPath(String absPath)
	{
		path = absPath;
	}

	public void putNormalizedPath(String sPath)
	{
		this.normalizedInputPath = sPath;
	}
	public void setReadOnly()
	{
		this.isReadOnly = true;
	}

	public void resetReadOnly()
	{
		this.isReadOnly = false;
	}
	public void putCachePath(String cpath)
	{
		this.cachePath= cpath;
	}

	/*Get methods*/
	public boolean getIsDir()
	{
		return this.isDir;
	}

	public long getLastModifiedTime()
	{
		return this.lastModifiedTime;
	}

	public long getFileSize()
	{
		return this.fileSize;
	}


	public int getErrorCode(int code)
	{
		return this.errorCode;
	}

	public String getPath()
	{
		return this.path ;
	}
	
}



