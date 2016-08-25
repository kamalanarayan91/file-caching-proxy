import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.io.File;
/**
 * Manages the proxy Cache.
 */
public class Cache
{

	
	private long currentSize;
	private long sizeLimit;

	//Contains file info. This is cachePath vs info
	public ConcurrentHashMap<String,CachedFileInfo> fileInfoMap;

	// contains the list of filenames in the cache.
	public LinkedList<CachedFileInfo> usageList;
	//MRU at head and LRU at Tail.
		
	//uses input path and last Modified time; - Facilitates deleting
	public ConcurrentHashMap<String,Long> fileVersionMap;

	/**
	 * Constructor
	 * @param  limit - Size Limit of cache
	 */
	public Cache(long limit)
	{
		this.sizeLimit = limit;
		this.currentSize = 0;
		this.fileInfoMap = new ConcurrentHashMap<String,CachedFileInfo>();
		this.fileVersionMap = new ConcurrentHashMap<String,Long>();
		this.usageList = new LinkedList<CachedFileInfo>();
	}

	/**
	 * Makes the file as the most recently used.
	 * @param file-file path
	 */
	public synchronized void makeMRU(CachedFileInfo file)
	{
		// If it is already there in linked List;
		if(usageList.contains(file))
		{
			int index = usageList.indexOf(file);
			CachedFileInfo tempFileInfo = usageList.get(index);
			usageList.remove(index);
			usageList.addFirst(tempFileInfo);
		}
		else
		{
			usageList.addFirst(file);
		}

	}

	/**
	 * returns true if the file can be fit.
	 * @param  fileSize 
	 * @return          Eviction status
	 */
	public synchronized boolean evictLRUFiles(long fileSize)
	{

		if(sizeLimit < fileSize)
		{
			return false;
		}

		//backwards iteration;
		for(int index = usageList.size()-1;index>-1; index--)
		{
			CachedFileInfo tempFile = usageList.get(index);
			System.err.println("Trying::"+ tempFile.cachePath + "size:"+ tempFile.fileSize);
			 // write files cannot be touched. as they will be 
			 // deleted at the end of the close operation.
			if(tempFile.isReadOnly)
			{
				System.err.println("ReadOnly");
				if(tempFile.readerCount == 0)
				{
					
					System.err.println("SizeBere:"+currentSize);
					evictFile(tempFile.cachePath);
					System.err.println("SizeAfter:"+currentSize);
				}

				if(isThereCacheSpace(fileSize))
				{
					return true;
				}
			}
		}

		return false;

	}


	/**
	 * Gets the file info object from cache
	 * @param  path file-cache path
	 * @return     
	 */
	public synchronized CachedFileInfo getFromCache(String path)
	{
		if(fileInfoMap.containsKey(path))
		{
			return fileInfoMap.get(path);
		}
		else
		{
			return null;
		}
	}

	/**
	 * Puts the file info object in the cache
	 * @param path cachePath
	 * @param file Fileinfo
	 */
	public synchronized void putInCache(String cachePath, CachedFileInfo file)
	{	
		System.err.println("PUTTING IN CacheE:"+cachePath+ ":::size:"+file.fileSize);
		

		//check if it exists
		if(fileInfoMap.containsKey(cachePath))
		{
			CachedFileInfo temp = fileInfoMap.get(cachePath);
			fileInfoMap.remove(cachePath);
			currentSize -= temp.fileSize;
		}

		fileInfoMap.put(cachePath,file);
		currentSize+=file.fileSize;

		
	}

	/**
	 * Checks if there is space in the cache to accomodate the file
	 * @param  fileSize 
	 * @return  status
	 */
	public synchronized boolean isThereCacheSpace(long fileSize)
	{
		return (currentSize + fileSize <= sizeLimit);
	}

	/**
	 * Evicts the file from the cache
	 * @param cachePath
	 */
	public synchronized void evictFile(String cachePath)
	{
		CachedFileInfo info = fileInfoMap.get(cachePath);

		//removed from list
		usageList.remove(info);

		//remove from Map
		fileInfoMap.remove(cachePath);

		//delete the file;
		File file = new File(cachePath);
		file.delete();

		currentSize -= info.fileSize;
	}

	
}