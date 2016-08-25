import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.io.File;


import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicInteger;

import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;


/**
 * Proxy: This class is used for caching server files and 
 * carrying out file operations from the client.
 *
 * It uses an open -close session semantics.
 */
class Proxy 
{

	public static final int SUCCESS = 0;
	public static final int NOERROR  = 200;
	public static final int DIRCODE = 100; 
	public static final int EIO = -5;
	public static final int MAXCHUNKSIZE = 1024 * 1024;
	public static final int FAILURE = -1;
	private static Cache cache;
	
	private static AtomicInteger uniqueFD = new AtomicInteger(100);

	private static String serverIP;
	private static String serverPort; 
	private static String cacheDir;
	private static long cacheSize;
	private static RemoteCallable rmiServer;
	
	
	/**
	 * Proxy constructor
	 * @param  arguments CommandLineArgs. 
	 * @return           
	 */
	public Proxy(String[] arguments)
	{
		this.serverIP = arguments[0];
		this.serverPort = arguments[1];

		try
		{
			//directory should be checked
			this.cacheDir = validateCachePath(arguments[2]);
			if(cacheDir == null)
			{
				
				System.exit(FAILURE);
			}

	
			this.cacheSize = Long.parseLong(arguments[3]); 

			rmiServer = (RemoteCallable) 
						  Naming.lookup("//"+serverIP+":"+serverPort+"/Server"); 
		}
		catch(Exception e)
		{
			System.err.print("Please Check if the Server ip and port");
			System.err.println(" arguments are correct");
			System.exit(-1);			
		}

		if(cacheSize<0)
		{
			System.err.println("Enter positive cacheSize");
			System.exit(-1);
		}


		this.cache = new Cache(cacheSize);
	}



	/**
	 * Validates the cache path.
	 * @param  cmdLinePath cache root directory
	 * @return             null if path is invalid
	 *                     absolute path if valid.
	 */
	public String validateCachePath(String cmdLinePath) throws RemoteException
	{
		
		File file = new File(cmdLinePath);
		try
		{
			//does it exist?
			if(!file.exists())
			{
				System.err.println("dire doesn't exist");
				file.mkdirs();
				
			}

			//is it a directory?
			if(!file.isDirectory())
			{
				return null;
				
			}

			//String resultPath = file.getCanonicalPath();
			String resultPath = cmdLinePath;

			//Append / if it it doesn't exist.
			if(resultPath.charAt(resultPath.length()-1) != '/')
			{
				resultPath = resultPath+ '/';
			}
			
			return resultPath;
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}


	/**
	 * FileHandler - For all the file operations.
	 */
	private static class FileHandler implements FileHandling 
	{

		//Used for fd- file mapping
		private ConcurrentHashMap<Integer,AccessFile> fdAccessFileMap;
		//list of open directories.
		private ArrayList<Integer> fdDirList;
		
		/**
		 * Constructor
		 * @return none.
		 */
		public FileHandler()
		{
						
			fdDirList = new ArrayList<Integer>();
			fdAccessFileMap= new ConcurrentHashMap<Integer,AccessFile>();
	
		}

		/**
		 * Creates a unique cache path name based on the version of the file
		 * 
		 * @param  origPath PathFromServer
		 * @param  version  LastModified Time of file
		 * @return         The uniquePath
		 */
		public String newCacheFileName(String origPath,long version) 
		{
			try
			{
			

			String temp = origPath.replaceAll("/","_");
			if(temp == null)
			{
				temp  = origPath;
			}

			System.err.println("replaced::"+temp);

			String result = getCacheFileAbsPath(temp);

			
			result = result + "_"+Long.toString(version);
			return result;
			}
			catch(Exception e )
			{
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * Get the absolute path of the file
		 * @param  path : inputPath.
		 * @return      absolute cachepath
		 */
		public String getCacheFileAbsPath(String path)
		{
			String cPath;
			
			
			if(cacheDir.endsWith("/"))
			{
				cPath = cacheDir+path;
			}
			else
			{
				cPath = cacheDir+"/"+path;
			}

			return cPath;
		}

		/**
		 * Downloads a file from the server.
		 * @param path          Server Path
		 * @param cacheFilePath 
		 * @param fileSize      Size of File.
		 */
		public void createFile(String path,String cacheFilePath,long fileSize)
		{
			//check for file existence.
			try
			{
				
				long offset = 0;
				long bytesRead = 0;
				
				RandomAccessFile raFile = new RandomAccessFile(cacheFilePath,"rw");
				long totalBytes = 0;
				do
				{
					
					Chunk chunk2 = new Chunk();
					chunk2 = rmiServer.downloadChunkFromServer(path,offset);

					if( chunk2.size < 0)
					{	
						break; //done
					}

					bytesRead = chunk2.size;

					offset += bytesRead;
					totalBytes += bytesRead;
					
					raFile.write (chunk2.buffer, 0, chunk2.size);
					
					raFile.seek(offset);
				}
				while(totalBytes < fileSize);
				raFile.close();
			}	
			catch(Exception e)
			{
				e.printStackTrace();
			}

		}

		/**
		 * creates a new copy of the file for that client to write to.
		 * @param path    oldFile Path
		 * @param newPath 
		 */
		public void createPrivateCopy(String path,String newPath)
		{
			try
			{
				System.err.println("path:"+path);
				System.err.println("newPath:"+newPath);
				Files.copy(Paths.get(path),Paths.get(newPath));
			}
			catch(Exception e)
			{
				System.err.println("Error in copying Files");
				e.printStackTrace();
			}
		}	

		/**
		 * Creates a unique file name based on the file descriptor and the
		 * cache path.
		 * @param path [description]
		 * @param fd   [description]
		 */
		public String getNewWriteFilePath(String path,int fd)
		{
			return path + "_" + Integer.toString(fd) +"_"+ "w";
											
		}

		/**
		 * Delete older versions of the file in cache, if a newer one is present
		 * in the server.
		 * @param path input file path from user.
		 */
		public boolean deleteStaleVersions(String path,CachedFileInfo fileInfo)
		{
			Long latestTime = cache.fileVersionMap.get(path);
			if(latestTime == null)
				return false;
			try
			{
				if(fileInfo.lastModifiedTime == latestTime)
				{
					return false; //latest copy.
				}
				if(fileInfo.lastModifiedTime<latestTime)
				{
					cache.evictFile(fileInfo.cachePath);
					return true;
				}
				else
				{
					System.err.println("current is higher than latestCache");
					System.err.println("Impossible!");
					return false;
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				System.err.println("Error in deleting old versions");
			}
			return false;
		}


		/**
		 * Open: 
		 * 1. Get version of file
		 * 2. If latest version is not in the cache, download it.
		 * 3. If the option is non-read, create a private copy.
		 * @param  path [description]
		 * @param  o    [description]
		 * @return      [description]
		 */
		public int open( String path, OpenOption o ) 
		{

			System.err.println("************OPEN CALLED*************");
			System.err.println("path:"+ path);
			System.err.println("option:"+ o.toString());
			String raFileMode = "rw";
			
			
			CachedFileInfo serverFileInfo;
			CachedFileInfo cacheFileInfo;


			try
			{
				 serverFileInfo = rmiServer.getFileInfo(path,o);
			}
			catch(RemoteException e)
			{
				e.printStackTrace();
				return Errors.EBUSY;
			}

			//Error Checking
			if(serverFileInfo.errorCode != NOERROR)
			{
				
				System.err.println("Error:"+serverFileInfo.errorCode);
				System.err.println("************OPEN FIN*************");
				return serverFileInfo.errorCode;
			}

			//Check if it is a directory:
			if(serverFileInfo.isDir)
			{	
				System.err.println("Is a Directory!");
				int resultFd = uniqueFD.getAndIncrement();
				fdDirList.add(resultFd);
				System.err.println("FD returned = " + resultFd);
				System.err.println("************OPEN FINISHED*************");
				return resultFd;				
			}


			// Let the server decide if the path is valid
			path = serverFileInfo.normalizedInputPath;

			//Old version is in cache
			//Check for any old Versions;
			Long cacheTime = cache.fileVersionMap.get(path);
			if(cacheTime != null)
			{
				//Delete if cache version is not in use and is stale;	
				if(cacheTime < serverFileInfo.lastModifiedTime)
				{
					String cachePath = newCacheFileName(path,cacheTime);
					CachedFileInfo temp = cache.getFromCache(cachePath);
					
					//Not in use;
					if(temp!=null)
					{
						if(temp.readerCount == 0)
						{
							cache.evictFile(cachePath);
						}
					}
				}

			}

			//Handles nested subdirs
			String cachePath = newCacheFileName(path,serverFileInfo.lastModifiedTime);
			System.err.println("cachePath:"+cachePath);

			//See if the latest version(as a read Copy) exists in Cache
			cacheFileInfo = cache.getFromCache(cachePath);

			if(cacheFileInfo == null)
			{
				System.err.println("CACHE MISS!!!");

				//copy constructor
				cacheFileInfo = new CachedFileInfo(serverFileInfo);

				if(!cache.isThereCacheSpace(serverFileInfo.fileSize))//No space
				{
					if(!cache.evictLRUFiles(serverFileInfo.fileSize))
					{
						return Errors.ENOMEM;
					}
				}
		
				//creating Master Copy
				createFile(path,cachePath,cacheFileInfo.fileSize);	
				cacheFileInfo.putCachePath(cachePath);
				cacheFileInfo.setReadOnly();
				cache.putInCache(cachePath,cacheFileInfo);
				cache.makeMRU(cacheFileInfo);

				//Update version Info in the cache Map
				cache.fileVersionMap.put(path,cacheFileInfo.lastModifiedTime);
				System.err.println("Path"+path+" "+"LM:"+cacheFileInfo.lastModifiedTime);
				
				try
				{
					System.err.println("new Master Created");
				

					if(o == OpenOption.READ)
					{
						//Read Only;
						raFileMode = "r";
						File file = new File(cachePath);
						RandomAccessFile rAccessFile
								 = new RandomAccessFile(file,raFileMode);

						AccessFile aFile
							 = new AccessFile(path,rAccessFile,raFileMode);
						aFile.putCachePath(cachePath);
						

						//unique fd
						int resultFd = uniqueFD.getAndIncrement();				
						Integer result = new Integer(resultFd);

						//Cache Related		
						//cacheFileInfo.putCachePath(cachePath);
						//cacheFileInfo.setReadOnly();
						cacheFileInfo.incrReaderCount();
						cache.putInCache(cachePath,cacheFileInfo);
						
						//local to client Map
						fdAccessFileMap.put(result,aFile);	
						cache.makeMRU(cacheFileInfo);

						System.err.println("FD returned = " + resultFd);
						System.err.println("************OPEN FINISHED*************");
						return resultFd;

					}
					else
					{

						//check cache Space!!
						if(!cache.isThereCacheSpace(cacheFileInfo.fileSize))//No space
						{
							if(!cache.evictLRUFiles(serverFileInfo.fileSize))
							{
								return Errors.ENOMEM;
							}
						}


						//Already has version numbers
						//cachePath contains the mastercopy's file path;						
						int resultFd = uniqueFD.getAndIncrement();
						String newPath =  getNewWriteFilePath(cachePath,resultFd);

						//creating a new private Copy;
						createPrivateCopy(cachePath,newPath);
						System.err.println("new Copy created:"+newPath);

						CachedFileInfo newInfo = new CachedFileInfo(cacheFileInfo);

						//update the object
						newInfo.putCachePath(newPath);



						// Assumption- Cache has space
						File file = new File(newPath);
						RandomAccessFile rAccessFile = new RandomAccessFile(file,raFileMode);
						AccessFile aFile = new AccessFile(path,rAccessFile,raFileMode);
						aFile.putCachePath(newPath);
						newInfo.resetReadOnly();

						//Cache Related
						Integer result = new Integer(resultFd);
						cache.putInCache(newPath,newInfo);

						//Internal to FileHandling
						fdAccessFileMap.put(result,aFile);	

						//what if mode is write?				
						System.err.println("FD returned = " + resultFd);
						System.err.println("************OPEN FINISHED*************");

						//make MRU;
						return resultFd; 		
					}
			
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}

			}
			else // File is in Cache! 
			{	try
				{
					System.err.println("CACHE HIT!!!");
					if(o == OpenOption.READ)
					{
						raFileMode = "r";

						//create FD;
						File file = new File(cachePath);
						RandomAccessFile rAccessFile
									 = new RandomAccessFile(file,raFileMode);

						AccessFile aFile
								 = new AccessFile(path,rAccessFile,raFileMode);
						aFile.putCachePath(cachePath);
						
						//unique fd
						int resultFd = uniqueFD.getAndIncrement();

						//Just increment reader count;
						cacheFileInfo.incrReaderCount();

						//File Handling related					
						Integer result = new Integer(resultFd);
						fdAccessFileMap.put(result,aFile);	

						cache.putInCache(cachePath,cacheFileInfo);

						//Make MRU;
						cache.makeMRU(cacheFileInfo);
						return resultFd;

					}
					else
					{
						
						//check cache Space!!
						if(!cache.isThereCacheSpace(cacheFileInfo.fileSize))//No space
						{
							if(!cache.evictLRUFiles(cacheFileInfo.fileSize))
							{
								return Errors.ENOMEM;
							}
						}

						//Have to copy the file info object~~
						CachedFileInfo newFileInfo = new CachedFileInfo(cacheFileInfo);

						//Already has version numbers
						//cachePath contains the mastercopy's file path;						
						int resultFd = uniqueFD.getAndIncrement();
						String newPath =  getNewWriteFilePath(cachePath,resultFd);

						//creating a new private Copy;
						createPrivateCopy(cachePath,newPath);
						System.err.println("new Copy created:"+newPath);

						// Assumption- Cache has space
						File file = new File(newPath);
						RandomAccessFile rAccessFile = new RandomAccessFile(file,raFileMode);
						AccessFile aFile = new AccessFile(path,rAccessFile,raFileMode);
						aFile.putCachePath(newPath);
						newFileInfo.putCachePath(newPath);
						
						//Cache Related
						Integer result = new Integer(resultFd);
						newFileInfo.resetReadOnly();
						cache.putInCache(newPath,newFileInfo);


						//Internal to FileHandling
						fdAccessFileMap.put(result,aFile);	

						//what if mode is write?				
						 System.err.println("FD returned = " + resultFd);
						 System.err.println("************OPEN FINISHED*************");

						 //make MRU;
						 cache.makeMRU(newFileInfo);
						 return resultFd;
					}
				}
					catch(Exception e)
					{
						e.printStackTrace();
					}
				
			}

			return FAILURE;

		}
				
		
		/**
		 * close the file.
		 * 1. If it is a write-private copy, send changes to server.
		 * 2. Delete it 
		 * 3. If it is a read copy, decrement the reader count and 
		 * delete if the version is obsolete.
		 * @param  fd file descriptor
		 * @return    error/success.
		 */
		public int close( int fd ) 
		{

			System.err.println("************CLOSE CREATED*************");
			System.err.println("FD :" + fd);


			//Dir Check
			if(fdDirList.contains(fd))
			{

				int index = fdDirList.indexOf(fd);
				fdDirList.remove(index);				
				System.err.println("directory closed");
				return 0;

			}	

			
			AccessFile aFile = fdAccessFileMap.get(fd);
			if(aFile == null)
			{
				
				System.err.println("Close: EBADF returned");
				return Errors.EBADF;
			}


			RandomAccessFile rAccessFile = aFile.rAFile;
			CachedFileInfo info = cache.getFromCache(aFile.cachePath);			

			//need to write back to server;
			if(!info.isReadOnly)
			{
				try
				{
					rmiServer.deleteOldVersion(aFile.inputPath);
					writeToServer(fd);
					long time = rmiServer.updateVersionNumber(info.path);
					System.err.println("Should delete"+ aFile.cachePath);
				}
				catch(RemoteException e)
				{
					e.printStackTrace();
					return Errors.EBUSY;
				}
			}


			//close file.
			try
			{
				rAccessFile.close();
			}
			catch(IOException e)
			{
				e.printStackTrace();
				System.err.println("Close: EIOreturned");
				return EIO;
			}



			//remove from cache and maps
			if(!info.isReadOnly)
			{

				cache.evictFile(aFile.cachePath);
				System.err.println("trying to del"+ aFile.cachePath);
				File file = new File(aFile.cachePath);
				file.delete();
				System.err.println("deleted"+ aFile.cachePath);
			}
			else
			{
				
				info.decrReaderCount();
				if(info.readerCount == 0)
				{

					if(!deleteStaleVersions(aFile.inputPath,info))
					{ // Not deleted.
						cache.putInCache(aFile.cachePath,info);
						cache.makeMRU(info);
					}
				}
				
			}


			fdAccessFileMap.remove(fd);

			System.err.println("************CLOSE Finished*************");
			return SUCCESS;//0 success
			
		}

		/**
		 * writeToServer: Writes the file to the server, if it is modified
		 * by this client.
		 * @param fd file descriptor.
		 */
		public void writeToServer(int fd)
		{


			try
			{
				AccessFile aFile = fdAccessFileMap.get(fd);

				//simply send write
				RandomAccessFile raFile = aFile.rAFile;
				raFile.seek(0);

				long bytesWritten = 0;
				long bytesRemaining = raFile.length();
				long offset = 0;

				while(bytesWritten <raFile.length())
				{

					raFile.seek(offset);
					Chunk chunk = new Chunk();

					if(bytesRemaining > MAXCHUNKSIZE)
					{
						chunk.buffer = new byte[MAXCHUNKSIZE];	
					}
					else
					{
						chunk.buffer = new byte[(int)bytesRemaining];		
					}

					
					chunk.size = raFile.read(chunk.buffer);
					chunk.offset = (int)offset;

					bytesRemaining -= chunk.size;
					offset += chunk.size;
					bytesWritten += chunk.size;
	
					//String path = fdPathMap.get(fd);
					String path = aFile.inputPath;
					rmiServer.uploadFileToServer(path,chunk);
				}

				raFile.close();
			}	
			catch(Exception e)
			{
				e.printStackTrace();
			}

		}


		/**
		 * Write to file only if the write operation won't make it exceed
		 * the cache size.
		 * @param  fd  file desciptor
		 * @param  buf bytes to write
		 * @return     error/no.of bytes written.
		 */
		public long write( int fd, byte[] buf ) 
		{
			
			if(fdDirList.contains(fd))
			{
				
				System.err.println("Writee: EISDIR returned");
				return Errors.EISDIR;
			}

			//check space in cache
			if(!cache.isThereCacheSpace(buf.length))//No space
					{
						if(!cache.evictLRUFiles(buf.length))
						{
							return Errors.ENOMEM;
						}
			}

			System.err.println("FD :" + fd);

			AccessFile aFile = fdAccessFileMap.get(fd);

			if(aFile==null)
			{

				System.err.println("Write: EBADF returned");
				return Errors.EBADF;
				
			}

			//fd is valid check permisssions
			String permissions = aFile.permissions;
			if(permissions.indexOf("w") == -1)
			{
				System.err.println("writee:Bad permissions");
				System.err.println("write: EBADF returned");
				return Errors.EBADF;
			}	
			
			RandomAccessFile rAccessFile = aFile.rAFile;

			try
			{
				rAccessFile.write(buf);
			}
			catch(IOException e)
			{

				System.err.println("Write: EIOreturned");
				return EIO;
			}

			//File was modified
			//
			aFile.isModified = true;
			aFile.rAFile = rAccessFile;
			fdAccessFileMap.put(fd,aFile);

			System.err.println("bytes written:"+ buf.length);
			System.err.println("************WRITE FINISHED*************");
			return buf.length;

		}

		/**
		 * similar to c read
		 * @param  fd  file descriptor
		 * @param  buf buffer for read bytes
		 * @return     error/no.of.bytes read
		 */
		public long read( int fd, byte[] buf )
		{

			System.err.println("************READ CALLED*************");
			System.err.println("FD :" + fd);

			//Dir check;
			if(fdDirList.contains(fd))
			{
				
				System.err.println("read: EISDIR returned");
				return Errors.EISDIR;
			}

			//check badf
			AccessFile aFile = fdAccessFileMap.get(fd);
			if(aFile == null)
			{
				
				System.err.println("read: EBADF returned");
				return Errors.EBADF;
			}



			//buf null
			if(buf == null)
			{
				System.err.println("read: EINVAL returned");
				return Errors.EINVAL;	
			}


			RandomAccessFile rAccessFile = aFile.rAFile;
			long result = -1;

			//check if file is over
			try
			{
				if(rAccessFile.getFilePointer() == rAccessFile.length())
				{
					return 0;
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();

			}



			try
			{
				result = rAccessFile.read(buf);

			}
			catch(IOException e)
			{

				System.err.println("read: EIOreturned");
				return EIO;
			}



			//resotre in amp
			aFile.rAFile =rAccessFile;
			fdAccessFileMap.put(fd,aFile);

			System.err.println("bytes read:"+ result);
			System.err.println("************READ FINISHED*************");
			return result;

		}

		/**
		 * 
		 * Similar to c - lseek function
		 * @param  fd  file descriptor
		 * @param  pos position to seek to.
		 * @param  o   options
		 * @return     error/success.
		 */
		public long lseek( int fd, long pos, LseekOption o ) {

			System.err.println("************LSEEK CALLED*************");
			System.err.println("FD :" + fd);
			System.err.println("pos :" + pos);
			System.err.println("options :" + o);


			//Dir check;
			if(fdDirList.contains(fd))
			{
				
				System.err.println("lseek: EISDIR returned");
				return Errors.EISDIR;
			}

			//check badf
			AccessFile aFile = fdAccessFileMap.get(fd);

			if(aFile == null )
			{
				
				System.err.println("lseek: EBADF returned");
				return Errors.EBADF;
			}

			RandomAccessFile rAccessFile = aFile.rAFile;

			// Check whence
			if(o == LseekOption.FROM_CURRENT)
			{
				try
				{
					long position = rAccessFile.getFilePointer() + pos;
					rAccessFile.seek(position);

				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}


			else if(o == LseekOption.FROM_END)
			{
				try
				{
					long position = rAccessFile.length()+pos;
					rAccessFile.seek(position);

				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}

			else if(o == LseekOption.FROM_START)
			{
				try
				{
					
					rAccessFile.seek(pos);

				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				System.err.println("Lseek: EINVAL returned");
				return Errors.EINVAL;
			}

			long result1 = -1;

			try
			{
				result1 = rAccessFile.getFilePointer();
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}

			//resotre in amp
			aFile.rAFile =rAccessFile;
			fdAccessFileMap.put(fd,aFile);

			System.err.println("************LSEEK FINISHED*************");
			return result1;



		}

		/**
		 * unlink: Deletes the remote file
		 * @param  path [description]
		 * @return      [description]
		 */
		public int unlink( String path )
		{
			System.err.println("************UNLINK STARTEDD*************");
			
			File file = new File(path);
			int retVal=-1;
			try
			{
				retVal=rmiServer.unlinkFile(path);
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return Errors.EBUSY;
			}

			if(retVal<0)
			{
				return retVal;
			}	

			System.err.println("************UNLINK FINISHED*************");
			return SUCCESS;
			//return Errors.ENOSYS;
		}

		/**
		 * clientdone: Closes all the open files(if any) for the 
		 * respective client.
		 */
		public void clientdone() {
	
			for(AccessFile aFile :fdAccessFileMap.values())
			{
				RandomAccessFile rAFile = aFile.rAFile;
				try
				{
					rAFile.close();
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}

			fdAccessFileMap.clear();
			return;
		}

	}

	/**
	 * returns a new File handler object
	 */
	private static class FileHandlingFactory implements FileHandlingMaking {
		public FileHandling newclient() 
		{

			return new FileHandler();
		}
	}

	/**
	 * starts the proxy.
	 * @param  args     
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		

		if(args.length!=4)
		{
			System.err.print("-usage: java Proxy <serverIP> <port>");
			System.err.println(" <cachedir> <cacheSize>");
			System.exit(-1);

		}
		Proxy proxy = new Proxy(args);		
		(new RPCreceiver(new FileHandlingFactory())).run();
	}
}

