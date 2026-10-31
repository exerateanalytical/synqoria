package org.synqoria.audit;
import org.synqoria.http.AuditedPatientAccess;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** P03-B0011: demonstrative local fsync audit chain, NOT a production immutable audit service.
 * No record payload, names or clinical notes are stored. Unavailable/corrupt storage denies read release.
 */
public final class BoundedAuditJournal implements AuditedPatientAccess.AuditSink {
 private static final long MAX_BYTES=1024*1024;
 private final Path path;
 public BoundedAuditJournal(Path file){path=Objects.requireNonNull(file).toAbsolutePath().normalize();}
 private static String hash(String v){try{
  byte[] dig=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(dig);
 }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
 private static String prior(String bytes){
  String prev="0".repeat(64);if(bytes.isEmpty())return prev;
  for(String line:bytes.split("\n",-1)){
   if(line.isEmpty())continue;
   String[] parts=line.split("\\|",-1);
   if(parts.length!=8 || !parts[0].equals(prev) || !parts[7].equals(hash(String.join("|",Arrays.copyOf(parts,7)))))throw new IllegalStateException("audit corruption");
   prev=parts[7];
  }return prev;
 }
 public boolean verify() {
  try{if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>MAX_BYTES)return false;
   prior(Files.readString(path));return true;
  }catch(IOException|RuntimeException ex){return false;}
 }
 @Override public boolean commit(UUID actor,UUID tenant,UUID facility,UUID patient,String operation,Instant now){
  if(actor==null||tenant==null||facility==null||patient==null||!"patient:read".equals(operation)||now==null)return false;
  try{
   Path dir=path.getParent();
   if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))return false;
   // Prevent symlink swaps; local host's file ownership/security still requires independent hardening.
   try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
       FileLock lock=channel.lock()){
    if(channel.size()>MAX_BYTES)return false;
    ByteBuffer buffer=ByteBuffer.allocate((int)channel.size());channel.position(0);
    while(buffer.hasRemaining()&&channel.read(buffer)>0){}buffer.flip();
    String previous=prior(StandardCharsets.UTF_8.decode(buffer).toString());
    String value=String.join("|",previous,actor.toString(),tenant.toString(),facility.toString(),patient.toString(),operation,now.toString());
    byte[] payload=(value+"|"+hash(value)+"\n").getBytes(StandardCharsets.UTF_8);
    if(channel.size()+payload.length>MAX_BYTES)return false;
    channel.position(channel.size());ByteBuffer data=ByteBuffer.wrap(payload);while(data.hasRemaining())channel.write(data);
    channel.force(true);
   }
   return true;
  }catch(IOException|RuntimeException ex){return false;}
 }
}
