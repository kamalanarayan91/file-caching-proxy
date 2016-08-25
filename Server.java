import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.rmi.registry.*;
import java.rmi.Naming;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;

import java.io.IOException;
import java.io.FileNotFoundException;

//paths
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;

/**
 * Main file Server:  Checks all the file info/ checks if operation is 
 * permitted.
 */
public class Server extends UnicastRemoteObject implements RemoteCallable
{
	private int port;
	private String rootDir;
	public static int counter = 1;

	public static final int SUCCESS = 0;
	public static final int DIRCODE = 100;
	public static final int NOERROR  = 200;
	public static final int INTIALVERSION = 1;
	public static final int MAXCHUNKSIZE = 1024 * 1024;	
	public static final int FAILURE = -1;

	
	//All relative to server Time; - Keeps track of last modified time of all the files
	//that are accessed in the server.
 	public static ConcurrentHashMap<String,Long> fileVersionMap;

 	/**
 	 * Constructor for server. 
 	 * @param  args            Command Line Arguments
 	 * @return                 
 	 * @throws RemoteException if any of the cmd line arguments are malformed
 	 */
	public Server(String[] args) throws RemoteException
	{
		super();
		port = Integer.parseInt(args[0]);
		rootDir = validateServerPath(args[1]);
		fileVersionMap = new ConcurrentHashMap<String,Long>();

		if(rootDir == null)
		{
			//These error prints are required.
			System.err.println("Please Enter Valid Path for Server");
			System.err.println("or Make sure that it exists");
			System.exit(FAILURE);
		}

		System.err.println(rootDir);

	}

	/**
	 * validateServerPath
	 * Validates the server path argument
	 * @param  cmdLinePath : Path from command line
	 * @return      null if path is invalid
	 *              absolutepath if it is valid
	 */

	public String validateServerPath(String cmdLinePath) throws RemoteException
	{
	
		File file = new File(cmdLinePath);
		try
		{
			//does it exist?
			if(!file.exists())
			{
				System.err.println("dire doesn't exist");
				return null;
			}

			//is it a directory?
			if(!file.isDirectory())
			{
				return null;
			}

			String resultPath = file.getCanonicalPath();
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
	 * validatePath: Validates the given file path. If it is within
	 * the server rootdirectory. This only checks the integrity of the
	 * path and not the file itself.
	 * @param  filePath filePath from client.
	 * @return     null - if the file is outside the root directory
	 *             absolutePath - if it is within the root dir.
	 */
	public String validatePath(String filePath)
	{

		String resultPath = rootDir+filePath;
		File file = new File(resultPath);
		try{
			String fileAbsPath = file.getCanonicalPath();
			if(fileAbsPath.startsWith(rootDir))
			{
				System.err.println("within dir:"+fileAbsPath);
				return fileAbsPath;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Extracts all the information about a particular server file.
	 * Returns the file Info object containing error codes/ various
	 * other information if applicable.
	 * @param  path Path of server file.
	 * @param  o    Open Mode.
	 * @return     FileInfo of the file in question.
	 */
	public CachedFileInfo getFileInfo(String path,FileHandling.OpenOption o)
	{
		
		String serverPath =  validatePath(path);
		

		if(serverPath == null)
		{
			System.err.println("Path Violation Detected:");
			CachedFileInfo cFileInfo = new CachedFileInfo();
			cFileInfo.putErrorCode(FileHandling.Errors.EPERM);
			return cFileInfo;
		}

		System.err.println(serverPath);

		File file = new File(serverPath);
		
		
		CachedFileInfo fileInfo = new CachedFileInfo();
		fileInfo.putPath(path);		

		if(file.isDirectory())
		{

			fileInfo.putIsDir(true);

			if(o != FileHandling.OpenOption.READ)
			{
				System.err.println("Open: EISDIR Returned");
				fileInfo.putErrorCode(FileHandling.Errors.EISDIR);
				return fileInfo;
			}			
		}
	
		//Check options.
		//Check various errors
		if(o == FileHandling.OpenOption.CREATE_NEW)
		{
			//file already exists
			if(file.exists())
			{
				System.err.println("Open-CREATE_NEW: EExist Returned");
				fileInfo.putErrorCode(FileHandling.Errors.EEXIST);
				return fileInfo;
			}

			try
			{
				file.createNewFile();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				System.err.println("Open-CREATE_NEW: EPERM Returned");
				fileInfo.putErrorCode(FileHandling.Errors.EPERM);
				return fileInfo;
			}
		}

		else if(o == FileHandling.OpenOption.CREATE)
		{
			//file doesn't exist
			if(!file.exists())
			{
				try
				{
					file.createNewFile();
				}
				catch(Exception e)
				{
					e.printStackTrace();
					System.err.println("OPEN-CREATE- ENOMEM Returned");
					fileInfo.putErrorCode(FileHandling.Errors.ENOMEM);
					return fileInfo;
				}
			}

			else if( !(file.canRead()) || !file.canWrite() )   
			{
				
				System.err.println("Open-CREATE: EPERM Returned");
				fileInfo.putErrorCode(FileHandling.Errors.EPERM);
				return fileInfo;
			}
				
		}
		else if(o == FileHandling.OpenOption.READ)
		{

			//file doesn't exist
			if(!file.exists())
			{
				System.err.println("Open-READ: ENOENT Returned");
				fileInfo.putErrorCode(FileHandling.Errors.ENOENT);
				return fileInfo;
			}
			else if (!file.canRead())
			{
				System.err.println("Open-READ: EPERM Returned");
				fileInfo.putErrorCode(FileHandling.Errors.EPERM);
				return fileInfo;
			}

		}
		else if(o == FileHandling.OpenOption.WRITE)
		{
			//file doesn't exist
			if(!file.exists())
			{
				System.err.println("Open-WRITE: ENOENT Returned");
				fileInfo.putErrorCode(FileHandling.Errors.ENOENT);
				return fileInfo;
			}

			//Cannot read or write
			else if (!file.canRead()|| !file.canWrite())
			{
				System.err.println("Open-WRITE: EPERM Returned");
				fileInfo.putErrorCode(FileHandling.Errors.EPERM);
				return fileInfo;
			}
		}
		else
		{
			System.err.println("Open: something wrong happening with modes");
			fileInfo.putErrorCode(FileHandling.Errors.EINVAL);
			return fileInfo;
		}


		
		fileInfo.putErrorCode(NOERROR);
		fileInfo.putFileSize(file.length());
		fileInfo.putLastModifiedTime(file.lastModified());
		fileInfo.putIsDir(false);
		fileInfo.putNormalizedPath(serverPath.substring(rootDir.length()));
		System.err.println("Normalized:"+serverPath.substring(rootDir.length()));
		
		fileVersionMap.put(serverPath,file.lastModified());

		
		System.err.println("Server file size:"+ file.length());
		System.err.println("[Server open]: PASS for file"+ serverPath);

		return fileInfo;
	}

	/**
	 * Sends the file chunk to the proxy
	 * @param  path            path in server
	 * @param  offset          byte offset within file
	 * @return                 chunk containg data/error
	 * @throws RemoteException
	 */
	public Chunk downloadChunkFromServer(String path,long offset) throws RemoteException
	{
		String sPath = validatePath(path);


		File file = new File(sPath);

		try
		{

			RandomAccessFile raFile = new RandomAccessFile(file,"r");


			raFile.seek(offset);

			Chunk chunk = new Chunk();

			if(raFile.getFilePointer() == raFile.length())
			{
				
				chunk.size = -1;
				raFile.close();
				return chunk;
			}

			chunk.buffer = new byte[MAXCHUNKSIZE];
			chunk.size = raFile.read(chunk.buffer);
			
			raFile.close();

			return chunk;
		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

		return null;

	}

	/**
	 * Gets the file from the client in chunks.
	 * @param  path            server file path
	 * @param  chunk           file chunk from client
	 * @throws RemoteException 
	 */
	public void uploadFileToServer(String path, Chunk chunk) throws RemoteException
	{	
		String sPath = validatePath(path);
		File file = new File(sPath);


		try
		{
			RandomAccessFile raFile = new RandomAccessFile(file,"rw");
			raFile.seek(chunk.offset);
			raFile.write(chunk.buffer,0,chunk.size);
			raFile.close();

		}
		catch(FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	

	}

	/**
	 * only get notified when the client wants to update the file
	 * @param  path            [description]
	 * @throws RemoteException [description]
	 */

	public void deleteOldVersion(String path) throws RemoteException
	{
		
		//if file is not updated by someone else,
		//delete it.
		String sPath = validatePath(path);
		File file = new File(sPath);
	
		if(file.exists())
		{
			file.delete();
			System.err.println("DeleteD!="+ sPath);
		}
		
	}

	/**
	 * updates the version hash map as well as sends back the
	 * last modified time of the file for further processing in the 
	 * cache.
	 * @param  path           	server relative file path
	 * @return                 [description]
	 * @throws RemoteException [description]
	 */
	public long updateVersionNumber(String path) throws RemoteException
	{
		String sPath = validatePath(path);
		
		File file = new File(sPath);
		fileVersionMap.put(sPath,file.lastModified());
		return file.lastModified();
	}

	public static void main(String[] args)
	{

		//args check
		if(args.length != 2)
		{
			System.err.println("Enter Proper Arguments");
			System.exit(-1);
		}

		
		int port = 0;
		try
		{
			port = Integer.parseInt(args[0]);
			
		}
		catch(NumberFormatException e)
		{
			System.err.println("Enter proper port number");
			System.exit(-1);
		}

		try
		{
			LocateRegistry.createRegistry(port);
			Server server = new Server(args);
			
			//Bind to  name
			Naming.rebind("//127.0.0.1:"+args[0]+"/Server",server);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}

	/**
	 * Delete the file in the server
	 * @param  path           server path
	 * @return                 status
	 * @throws RemoteException 
	 */

	public synchronized int unlinkFile(String path) throws RemoteException
	{
			String sPath = validatePath(path);
			File file = new File(sPath);

			if(!file.exists())	
			{
				System.err.println("unlink:ENOENT returned");
				return FileHandling.Errors.ENOENT;
			}

			if(file.isDirectory())
			{
				System.err.println("unlink: EISDIR returned");
				return FileHandling.Errors.EISDIR;
			}

			if(!file.canWrite())
			{
				System.err.println("unlink: EPERM returned");
				return FileHandling.Errors.EPERM;
			}
					

			file.delete();

			System.err.println("************Server UNLINK FINISHED*************");

			return SUCCESS;
	}
}




