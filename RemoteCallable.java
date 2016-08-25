import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface defining the RMI server.
 */
public interface RemoteCallable extends Remote
{
	public CachedFileInfo getFileInfo(String path,FileHandling.OpenOption o) throws RemoteException;
	public Chunk downloadChunkFromServer(String path,long offset) throws RemoteException;
	
	public void uploadFileToServer(String path, Chunk chunk) throws RemoteException;
	public void deleteOldVersion(String path) throws RemoteException;
	public long updateVersionNumber(String path) throws RemoteException;
	public int unlinkFile(String path) throws RemoteException;
 
	
}