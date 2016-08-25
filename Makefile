all: Cache.class AccessFile.class CachedFileInfo.class Server.class Proxy.class Cache.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
