package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.*;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public class ProjectPackageManager {
    public static final String MIME="application/vnd.elektoryk.project";
    public static final int SCHEMA=6;
    private static final long MB=1024L*1024L;

    public static class ImportResult{
        public String projectId="";
        public boolean createdCopy;
        public String message="";
    }

    public File createPackage(Context c,String projectId,boolean full) throws Exception{
        // Keep scene.json + points.bin on one committed generation for the whole export. Scanner,
        // editor and exporter use different repository objects, so the shared SceneRepository lock
        // is the only way to prevent a full package from catching a paired commit halfway through.
        synchronized(SceneRepository.IO_LOCK){return createPackageLocked(c,projectId,full);}
    }

    private File createPackageLocked(Context c,String projectId,boolean full) throws Exception{
        ProjectIdGuard.requireSafe(projectId);
        FileProjectRepository projects=new FileProjectRepository(c);
        FloorPlanRepository floors=new FloorPlanRepository(c);
        SceneRepository scenes=new SceneRepository(c);
        FieldWorkRepository field=new FieldWorkRepository(c);
        CommercialRepository commercialRepo=new CommercialRepository(c);
        RevisionRepository revisionRepo=new RevisionRepository(c);
        EvidenceRepository evidenceRepo=new EvidenceRepository(c);
        ApprovalRepository approvalRepo=new ApprovalRepository(c);
        ObjectFlowRepository objectFlowRepo=new ObjectFlowRepository(c);
        ElectricalProject project=projects.get(projectId);if(project==null)throw new IllegalArgumentException("Project not found");
        FloorPlan floor=floors.load(projectId);SceneSnapshot scene=scenes.loadScene(projectId);CableCutList cuts=field.loadCuts(projectId);FieldWorkPlan plan=field.loadPlan(projectId);
        CommercialPlan commercial=commercialRepo.load(projectId);RevisionHistory revisions=revisionRepo.load(projectId);EvidenceIndex evidence=evidenceRepo.load(projectId);ClientApproval approval=approvalRepo.load(projectId);ObjectFlowState objectFlow=objectFlowRepo.load(projectId);

        LinkedHashMap<String,byte[]> textFiles=new LinkedHashMap<>();
        textFiles.put("project.json",project.toJson().toString(2).getBytes("UTF-8"));
        textFiles.put("floorplan.json",floor.toJson().toString(2).getBytes("UTF-8"));
        if(scene!=null)textFiles.put("scene/scene.json",scene.toJson().toString(2).getBytes("UTF-8"));
        if(cuts!=null)textFiles.put("fieldwork/cuts.json",cuts.toJson().toString(2).getBytes("UTF-8"));
        if(plan!=null)textFiles.put("fieldwork/plan.json",plan.toJson().toString(2).getBytes("UTF-8"));
        if(commercial!=null)textFiles.put("commercial/plan.json",commercial.toJson().toString(2).getBytes("UTF-8"));
        if(revisions!=null)textFiles.put("revisions/history.json",revisions.toJson().toString(2).getBytes("UTF-8"));
        if(evidence!=null)textFiles.put("evidence/index.json",evidence.toJson().toString(2).getBytes("UTF-8"));
        if(approval!=null)textFiles.put("approval/approval.json",approval.toJson().toString(2).getBytes("UTF-8"));
        if(objectFlow!=null)textFiles.put("flow/state.json",objectFlow.toJson().toString(2).getBytes("UTF-8"));

        File sceneDir=new File(new File(c.getFilesDir(),"scenes"),projectId);
        File points=new File(sceneDir,"points.bin");
        File textureDir=new File(sceneDir,"textures");
        List<String> sceneTextures=full?referencedSceneTextures(scene):Collections.<String>emptyList();
        JSONObject checksums=new JSONObject();
        for(Map.Entry<String,byte[]> e:textFiles.entrySet())checksums.put(e.getKey(),sha256(e.getValue()));
        if(full&&points.isFile())checksums.put("scene/points.bin",sha256(points));
        if(full)for(String n:sceneTextures){File tf=new File(textureDir,n);if(tf.isFile())checksums.put("scene/textures/"+n,sha256(tf));}
        if(full&&evidence!=null)for(EvidenceItem item:evidence.items){String n=safeFileName(item.fileName);if(n.isEmpty())continue;File ef=evidenceRepo.file(projectId,item);if(ef.isFile())checksums.put("evidence/files/"+n,sha256(ef));}
        File signature=approvalRepo.signatureFile(projectId);boolean includeSignature=approval!=null&&!approval.signatureFile.isEmpty()&&signature.isFile();if(includeSignature)checksums.put("approval/signature.png",sha256(signature));
        if(full&&objectFlow!=null)for(String name:objectFlow.consultationAudioFiles){String n=safeFileName(name);if(n.isEmpty())continue;File af=new File(objectFlowRepo.audioDir(projectId),n);if(af.isFile())checksums.put("flow/audio/"+n,sha256(af));}

        JSONObject manifest=new JSONObject();
        manifest.put("format","ELEKTORYK_PROJECT");manifest.put("schema",SCHEMA);manifest.put("projectId",project.id);
        manifest.put("updatedAt",project.updatedAt);manifest.put("full",full);manifest.put("createdAt",System.currentTimeMillis());manifest.put("checksums",checksums);

        File dir=new File(c.getCacheDir(),"exports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create export directory");
        String safe=project.displayName().replaceAll("[^a-zA-Z0-9А-Яа-яІіЇїЄє_-]+","_");if(safe.isEmpty())safe="project";if(safe.length()>80)safe=safe.substring(0,80);
        String shortId=project.id.length()>8?project.id.substring(0,8):project.id;
        File out=new File(dir,safe+"_"+(full?"FULL":"LIGHT")+"_"+shortId+"_"+System.currentTimeMillis()+".elektoryk");
        File tmp=new File(dir,out.getName()+".tmp");if(tmp.exists())tmp.delete();
        // A FULL export can be hundreds of MB. Do not publish a package until the complete ZIP
        // central directory and all file data have reached the filesystem. Previously the stream
        // was merely closed and the .tmp file was renamed/copied; a process/storage failure around
        // that boundary could leave a package that existed by name but was not durably complete.
        try(FileOutputStream fos=new FileOutputStream(tmp);BufferedOutputStream bos=new BufferedOutputStream(fos);ZipOutputStream z=new ZipOutputStream(bos)){
            putBytes(z,"manifest.json",manifest.toString(2).getBytes("UTF-8"));
            for(Map.Entry<String,byte[]> e:textFiles.entrySet())putBytes(z,e.getKey(),e.getValue());
            if(full&&points.isFile())putFileVerified(z,"scene/points.bin",points,checksums.optString("scene/points.bin",""));
            if(full)for(String n:sceneTextures){File tf=new File(textureDir,n);if(tf.isFile())putFileVerified(z,"scene/textures/"+n,tf,checksums.optString("scene/textures/"+n,""));}
            if(full&&evidence!=null)for(EvidenceItem item:evidence.items){String n=safeFileName(item.fileName);if(n.isEmpty())continue;File ef=evidenceRepo.file(projectId,item);if(ef.isFile())putFileVerified(z,"evidence/files/"+n,ef,checksums.optString("evidence/files/"+n,""));}
            if(includeSignature)putFileVerified(z,"approval/signature.png",signature,checksums.optString("approval/signature.png",""));
            if(full&&objectFlow!=null)for(String name:objectFlow.consultationAudioFiles){String n=safeFileName(name);if(n.isEmpty())continue;File af=new File(objectFlowRepo.audioDir(projectId),n);if(af.isFile())putFileVerified(z,"flow/audio/"+n,af,checksums.optString("flow/audio/"+n,""));}
            z.finish();z.flush();bos.flush();fos.getFD().sync();
        }catch(Exception e){if(tmp.exists())tmp.delete();throw e;}
        if(!tmp.isFile()||tmp.length()<64){if(tmp.exists())tmp.delete();throw new IOException("Export package staging was incomplete");}
        // Use the common rollback-aware binary commit instead of a direct rename/copy fallback.
        // The timestamped target is normally new, but this also makes retries/collisions safe.
        AtomicFileStore.replaceBinary(out,tmp);
        if(!out.isFile()||out.length()<64)throw new IOException("Export package was not committed");
        trimExportCache(dir,out,24);return out;
    }

    public ImportResult importPackage(Context c,InputStream input) throws Exception{return importPackageInternal(c,input,false,false);}
    public ImportResult importPackage(Context c,InputStream input,boolean forceCopy) throws Exception{return importPackageInternal(c,input,forceCopy,false);}

    private ImportResult importPackageInternal(Context c,InputStream input,boolean forceCopy,boolean recoveryRestore) throws Exception{
        ImportPayload payload=readPayload(c,input);
        try{return commitPayload(c,payload,forceCopy,recoveryRestore);}finally{payload.close();}
    }

    private ImportResult commitPayload(Context c,ImportPayload files,boolean forceCopy,boolean recoveryRestore)throws Exception{
        JSONObject manifest=json(files,"manifest.json");
        if(!"ELEKTORYK_PROJECT".equals(manifest.optString("format","")))throw new IOException("Not an ELEKTORYK project");
        int schema=manifest.optInt("schema",0);if(schema<1||schema>SCHEMA)throw new IOException("Unsupported ELEKTORYK package schema: "+schema);
        boolean fullPackage=manifest.optBoolean("full",false);
        if(schema>=2)validateChecksums(files,manifest.optJSONObject("checksums"),schema>=5);
        validatePackageShape(files,fullPackage);

        ElectricalProject imported=ElectricalProject.fromJson(json(files,"project.json"));
        if(imported==null||!ProjectIdGuard.isSafe(imported.id))throw new IOException("Invalid/unsafe project ID");
        String manifestId=manifest.optString("projectId","");if(!manifestId.isEmpty()&&(!ProjectIdGuard.isSafe(manifestId)||!manifestId.equals(imported.id)))throw new IOException("Project ID mismatch");
        FloorPlan floor=files.contains("floorplan.json")?FloorPlan.fromJson(json(files,"floorplan.json")):new FloorPlan();
        SceneSnapshot scene=files.contains("scene/scene.json")?SceneSnapshot.fromJson(json(files,"scene/scene.json")):null;
        CableCutList cuts=files.contains("fieldwork/cuts.json")?CableCutList.fromJson(json(files,"fieldwork/cuts.json")):new CableCutList();
        FieldWorkPlan fieldPlan=files.contains("fieldwork/plan.json")?FieldWorkPlan.fromJson(json(files,"fieldwork/plan.json")):new FieldWorkPlan();
        CommercialPlan commercial=files.contains("commercial/plan.json")?CommercialPlan.fromJson(json(files,"commercial/plan.json")):new CommercialPlan();
        RevisionHistory revisions=files.contains("revisions/history.json")?RevisionHistory.fromJson(json(files,"revisions/history.json")):new RevisionHistory();
        EvidenceIndex evidence=files.contains("evidence/index.json")?EvidenceIndex.fromJson(json(files,"evidence/index.json")):new EvidenceIndex();
        ClientApproval approval=files.contains("approval/approval.json")?ClientApproval.fromJson(json(files,"approval/approval.json")):new ClientApproval();
        ObjectFlowState objectFlow=files.contains("flow/state.json")?ObjectFlowState.fromJson(json(files,"flow/state.json")):new ObjectFlowState();
        sanitizeImportedSceneTextures(scene,files);

        FileProjectRepository projects=new FileProjectRepository(c);ElectricalProject existing=projects.get(imported.id);ImportResult result=new ImportResult();String target=imported.id;File recoveryPackage=null;
        if(recoveryRestore){target=imported.id;}
        else if(forceCopy&&existing!=null){target=UUID.randomUUID().toString();result.createdCopy=true;imported.name=(imported.displayName()+" • recovery");}
        else if(existing!=null&&existing.updatedAt>imported.updatedAt){target=UUID.randomUUID().toString();result.createdCopy=true;imported.name=(imported.displayName()+" • імпорт");}
        else if(existing!=null){recoveryPackage=createRecoveryCopy(c,existing.id);if(recoveryPackage==null||!recoveryPackage.isFile())throw new IOException("Не вдалося створити recovery-копію перед оновленням. Імпорт зупинено без зміни поточного об'єкта.");}

        boolean targetExistedBefore=projects.get(target)!=null;SceneSnapshot localSceneBefore=null;boolean preserveLocal3d=false;
        if(!fullPackage&&targetExistedBefore){SceneRepository localScenes=new SceneRepository(c);if(localScenes.hasPointCloud(target)){localSceneBefore=localScenes.loadScene(target);preserveLocal3d=localSceneBefore!=null;}}
        sanitizeAndReconcileBinaryRefs(c,target,files,evidence,objectFlow,fullPackage);
        boolean hasSignature=files.contains("approval/signature.png");if(hasSignature){approval.signatureFile="signature.png";}else{approval.signatureFile="";approval.signatureAt=0;}
        imported.id=target;floor.projectId=target;if(scene!=null)scene.projectId=target;cuts.projectId=target;fieldPlan.projectId=target;commercial.projectId=target;revisions.projectId=target;evidence.projectId=target;approval.projectId=target;objectFlow.projectId=target;

        try{
            projects.save(imported);new FloorPlanRepository(c).save(floor);
            SceneRepository sceneRepo=new SceneRepository(c);
            if(preserveLocal3d)reportLight3dPreserved(c,target,localSceneBefore,scene);
            else if(fullPackage||scene!=null){
                synchronized(SceneRepository.IO_LOCK){
                    sceneRepo.importSceneState(target,scene,files.file("scene/points.bin"),fullPackage);
                    if(fullPackage)replaceSceneTextures(c,target,scene,files);
                }
            }
            FieldWorkRepository fw=new FieldWorkRepository(c);fw.saveCuts(cuts);fw.savePlan(fieldPlan);
            new CommercialRepository(c).save(commercial);new RevisionRepository(c).save(revisions);new EvidenceRepository(c).save(evidence);
            EvidenceRepository er=new EvidenceRepository(c);for(EvidenceItem item:evidence.items){String n=safeFileName(item.fileName);File ev=files.file("evidence/files/"+n);if(ev!=null)writeFileAtomic(new File(er.projectDir(target),n),ev);}
            ApprovalRepository ar=new ApprovalRepository(c);ar.save(approval);File signatureFile=files.file("approval/signature.png");if(signatureFile!=null)writeFileAtomic(ar.signatureFile(target),signatureFile);else{File oldSignature=ar.signatureFile(target);if(oldSignature.exists()&&!oldSignature.delete())throw new IOException("Could not remove obsolete signature");}
            ObjectFlowRepository ofr=new ObjectFlowRepository(c);ofr.save(objectFlow);for(String name:objectFlow.consultationAudioFiles){String n=safeFileName(name);File audio=files.file("flow/audio/"+n);if(audio!=null)writeFileAtomic(new File(ofr.audioDir(target),n),audio);}
            if(fullPackage)cleanupFullBinaryState(er,ofr,target,evidence,objectFlow);
        }catch(Exception e){
            StabilityReporter reporter=new StabilityReporter(c);reporter.error("IMPORT","Import commit failed for "+target,e);
            if(!recoveryRestore&&recoveryPackage!=null&&recoveryPackage.isFile()){
                try(InputStream rollback=new BufferedInputStream(new FileInputStream(recoveryPackage))){importPackageInternal(c,rollback,false,true);reporter.breadcrumb("IMPORT_ROLLBACK","Restored "+target+" from "+recoveryPackage.getName());}
                catch(Exception rollbackError){reporter.error("IMPORT_ROLLBACK","Rollback failed for "+target,rollbackError);}
            }else if(!targetExistedBefore){deleteProjectData(c,target);reporter.breadcrumb("IMPORT_CLEANUP","Removed partial new import "+target);}
            throw e;
        }
        result.projectId=target;result.message=result.createdCopy?"Імпортовано як копію, бо на цьому пристрої є новіша версія.":"Об'єкт імпортовано і перевірено.";if(preserveLocal3d)result.message+=" Локальний FULL 3D-скан збережено без змішування з LIGHT-пакетом.";return result;
    }

    private ImportPayload readPayload(Context c,InputStream input)throws Exception{
        if(input==null)throw new IOException("Package stream missing");File base=new File(c.getCacheDir(),"import_staging");if(!base.exists()&&!base.mkdirs())throw new IOException("Cannot create import staging");File dir=new File(base,"pkg_"+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,8));if(!dir.mkdirs())throw new IOException("Cannot create import staging package");ImportPayload payload=new ImportPayload(dir);
        long usable=c.getCacheDir().getUsableSpace();if(usable<16L*MB){payload.close();throw new IOException("Недостатньо вільного місця для безпечного імпорту");}long totalLimit=Math.min(512L*MB,Math.max(8L*MB,(usable*3L)/4L));long packageTotal=0;int entries=0;
        try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(input))){
            ZipEntry e;byte[] buf=new byte[16384];while((e=z.getNextEntry())!=null){
                if(++entries>2048)throw new IOException("Too many package entries");String rawName=e.getName();if(e.isDirectory()){String d=rawName==null?null:rawName.replace('\\','/');while(d!=null&&d.endsWith("/"))d=d.substring(0,d.length()-1);if(d!=null&&!d.isEmpty()&&safeEntry(d)==null)throw new IOException("Unsafe package directory");continue;}String name=safeEntry(rawName);if(name==null)throw new IOException("Unsafe package entry");if(!allowedEntry(name))throw new IOException("Unsupported package entry: "+name);if(payload.contains(name))throw new IOException("Duplicate package entry: "+name);
                long limit=entryLimit(name);MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
                if(binaryEntry(name)){
                    File staged=new File(dir,"bin_"+entries+".part");try(FileOutputStream fos=new FileOutputStream(staged);BufferedOutputStream out=new BufferedOutputStream(fos)){int n;while((n=z.read(buf))>0){total+=n;packageTotal+=n;if(total>limit)throw new IOException("Package entry too large: "+name);if(packageTotal>totalLimit)throw new IOException("FULL пакет завеликий для безпечного імпорту на цьому пристрої");digest.update(buf,0,n);out.write(buf,0,n);}out.flush();fos.getFD().sync();}if(total<=0)throw new IOException("Empty binary package entry: "+name);payload.addFile(name,staged,hex(digest.digest()));
                }else{
                    ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=z.read(buf))>0){total+=n;packageTotal+=n;if(total>limit)throw new IOException("Package JSON too large: "+name);if(packageTotal>totalLimit)throw new IOException("Package too large for safe import");digest.update(buf,0,n);out.write(buf,0,n);}payload.addBytes(name,out.toByteArray(),hex(digest.digest()));
                }
            }
        }catch(Exception ex){payload.close();throw ex;}
        return payload;
    }

    private boolean allowedEntry(String name){
        if("manifest.json".equals(name)||"project.json".equals(name)||"floorplan.json".equals(name)||"scene/scene.json".equals(name)||"scene/points.bin".equals(name)||"fieldwork/cuts.json".equals(name)||"fieldwork/plan.json".equals(name)||"commercial/plan.json".equals(name)||"revisions/history.json".equals(name)||"evidence/index.json".equals(name)||"approval/approval.json".equals(name)||"approval/signature.png".equals(name)||"flow/state.json".equals(name))return true;
        if(name.startsWith("scene/textures/")){String n=name.substring("scene/textures/".length());return StoredFileNameGuard.isSafeLeaf(n);}
        if(name.startsWith("evidence/files/")){String n=name.substring("evidence/files/".length());return StoredFileNameGuard.isSafeLeaf(n);}
        if(name.startsWith("flow/audio/")){String n=name.substring("flow/audio/".length());return StoredFileNameGuard.isSafeLeaf(n);}
        return false;
    }
    private void validatePackageShape(ImportPayload files,boolean fullPackage)throws IOException{
        boolean hasPoints=files.contains("scene/points.bin");
        boolean hasEvidenceFiles=false,hasAudioFiles=false,hasTextures=false;
        for(String name:files.names()){if(name.startsWith("scene/textures/"))hasTextures=true;if(name.startsWith("evidence/files/"))hasEvidenceFiles=true;if(name.startsWith("flow/audio/"))hasAudioFiles=true;}
        if(!fullPackage&&(hasPoints||hasTextures||hasEvidenceFiles||hasAudioFiles))throw new IOException("LIGHT пакет містить FULL-only дані");
        if((hasPoints||hasTextures)&&!files.contains("scene/scene.json"))throw new IOException("Scene binary data без scene.json");
        if(hasEvidenceFiles&&!files.contains("evidence/index.json"))throw new IOException("Evidence files без evidence index");
        if(hasAudioFiles&&!files.contains("flow/state.json"))throw new IOException("Audio files без flow state");
        if(files.contains("approval/signature.png")&&!files.contains("approval/approval.json"))throw new IOException("Signature без approval state");
    }

    private boolean binaryEntry(String name){return "scene/points.bin".equals(name)||name.startsWith("scene/textures/")||"approval/signature.png".equals(name)||name.startsWith("evidence/files/")||name.startsWith("flow/audio/");}
    private long entryLimit(String name){if("manifest.json".equals(name))return 1L*MB;if("scene/points.bin".equals(name))return 64L*MB;if(name.startsWith("scene/textures/"))return 16L*MB;if("approval/signature.png".equals(name))return 5L*MB;if(name.startsWith("evidence/files/"))return 26L*MB;if(name.startsWith("flow/audio/"))return 128L*MB;return 8L*MB;}

    private List<String> referencedSceneTextures(SceneSnapshot scene){
        LinkedHashSet<String> out=new LinkedHashSet<>();if(scene==null)return new ArrayList<>(out);collectTextureRefs(scene.apartmentPlan,out);collectTextureRefs(scene.latestAutoApartmentPlan,out);return new ArrayList<>(out);
    }
    private void collectTextureRefs(ApartmentPlan plan,Set<String> out){
        if(plan==null)return;for(PlanWallSegment w:plan.walls){if(w==null||!StoredFileNameGuard.isSafeLeaf(w.scannedTextureFile))continue;out.add(w.scannedTextureFile);}
    }
    private void sanitizeImportedSceneTextures(SceneSnapshot scene,ImportPayload files){
        if(scene==null)return;sanitizeTextureRefs(scene.apartmentPlan,files);sanitizeTextureRefs(scene.latestAutoApartmentPlan,files);
    }
    private void sanitizeTextureRefs(ApartmentPlan plan,ImportPayload files){
        if(plan==null)return;for(PlanWallSegment w:plan.walls){if(w==null)continue;String n=w.scannedTextureFile;if(!StoredFileNameGuard.isSafeLeaf(n)||!files.contains("scene/textures/"+n)){w.scannedTextureFile="";w.scannedTextureCoverage=0;}}
    }
    private void replaceSceneTextures(Context c,String target,SceneSnapshot scene,ImportPayload files)throws Exception{
        File sceneDir=new File(new File(c.getFilesDir(),"scenes"),ProjectIdGuard.requireSafe(target));File texDir=new File(sceneDir,"textures");
        recoverInterruptedTextureSwap(sceneDir,texDir);
        File staged=new File(sceneDir,"textures.importnew");deleteRecursive(staged);if(!staged.exists()&&!staged.mkdirs())throw new IOException("Cannot stage scene textures");
        boolean copied=false;for(String n:referencedSceneTextures(scene)){File source=files.file("scene/textures/"+n);if(source==null)continue;writeFileAtomic(new File(staged,n),source);copied=true;}
        commitTextureDirectory(sceneDir,texDir,staged,copied);
    }

    private void recoverInterruptedTextureSwap(File sceneDir,File texDir)throws IOException{
        File backup=new File(sceneDir,"textures.importbak");
        if(texDir.isDirectory()){deleteRecursive(backup);return;}
        if(backup.isDirectory()&&!backup.renameTo(texDir))throw new IOException("Cannot recover interrupted texture generation swap");
    }

    /** Commit a complete texture generation without exposing a half-empty directory.
     *  Both directories live under the same scene directory, so rename is an atomic metadata
     *  operation on normal Android app storage. The previous generation is kept until the new
     *  one is visible and is restored if the swap fails.
     */
    private void commitTextureDirectory(File sceneDir,File texDir,File staged,boolean copied)throws IOException{
        File backup=new File(sceneDir,"textures.importbak");deleteRecursive(backup);
        boolean hadOld=texDir.isDirectory();
        if(hadOld&&!texDir.renameTo(backup))throw new IOException("Cannot preserve previous texture generation");
        try{
            if(copied){
                if(!staged.renameTo(texDir))throw new IOException("Cannot publish staged texture generation");
            }else{
                // A valid FULL package with no referenced textures intentionally publishes an
                // empty generation. Do not leave old, now-unreferenced bitmaps behind.
                if(!texDir.exists()&&!texDir.mkdirs())throw new IOException("Cannot publish empty texture generation");
                deleteRecursive(staged);
            }
            deleteRecursive(backup);
        }catch(IOException failure){
            deleteRecursive(texDir);
            if(hadOld&&backup.isDirectory()&&!backup.renameTo(texDir))
                failure.addSuppressed(new IOException("Could not restore previous texture generation"));
            deleteRecursive(staged);
            throw failure;
        }
    }

    private void trimExportCache(File dir,File current,int keep){File[] all=dir.listFiles();if(all==null)return;List<File> packages=new ArrayList<>();for(File f:all)if(f.isFile()&&f.getName().endsWith(".elektoryk")&&!f.equals(current))packages.add(f);if(packages.size()<keep)return;Collections.sort(packages,new Comparator<File>(){@Override public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});for(int i=Math.max(0,keep-1);i<packages.size();i++)packages.get(i).delete();}

    private File createRecoveryCopy(Context c,String projectId){
        try{ProjectIdGuard.requireSafe(projectId);File pkg=createPackage(c,projectId,true);File dir=new File(c.getFilesDir(),"recovery_packages");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create recovery directory");File target=new File(dir,projectId+"_before_import_"+System.currentTimeMillis()+".elektoryk");File tmp=new File(target.getAbsolutePath()+".tmp");copy(pkg,tmp);AtomicFileStore.replaceBinary(target,tmp);trimRecovery(dir,projectId,5);new StabilityReporter(c).breadcrumb("IMPORT_BACKUP","Created "+target.getName());return target;}
        catch(Exception e){new StabilityReporter(c).error("IMPORT_BACKUP","Could not create pre-import backup",e);return null;}
    }
    private void trimRecovery(File dir,String projectId,int keep){File[] all=dir.listFiles();if(all==null)return;List<File> f=new ArrayList<>();String prefix=projectId+"_before_import_";for(File x:all)if(x.isFile()&&x.getName().startsWith(prefix)&&x.getName().endsWith(".elektoryk"))f.add(x);if(f.size()<=keep)return;Collections.sort(f,new Comparator<File>(){@Override public int compare(File a,File b){return Long.compare(b.lastModified(),a.lastModified());}});for(int i=keep;i<f.size();i++)f.get(i).delete();}

    private void validateChecksums(ImportPayload files,JSONObject sums,boolean requireAllPayloads)throws Exception{
        if(sums==null)throw new IOException("Missing package checksums");if(requireAllPayloads)for(String name:files.names()){if("manifest.json".equals(name))continue;if(!sums.has(name))throw new IOException("Unchecked package entry: "+name);}
        Iterator<String> it=sums.keys();while(it.hasNext()){String name=it.next();if(!files.contains(name))throw new IOException("Missing checked entry: "+name);String expected=sums.optString(name,"");if(expected.length()!=64)throw new IOException("Invalid checksum: "+name);String actual=files.sha(name);if(!expected.equalsIgnoreCase(actual))throw new IOException("Checksum mismatch: "+name);}
    }

    private void sanitizeAndReconcileBinaryRefs(Context c,String target,ImportPayload files,EvidenceIndex evidence,ObjectFlowState objectFlow,boolean fullPackage){
        EvidenceRepository er=new EvidenceRepository(c);Iterator<EvidenceItem> evidenceIt=evidence.items.iterator();while(evidenceIt.hasNext()){EvidenceItem item=evidenceIt.next();String original=item.fileName;if(!StoredFileNameGuard.isSafeLeaf(original)){evidenceIt.remove();continue;}String safe=original;item.fileName=safe;boolean inPackage=files.contains("evidence/files/"+safe);boolean localExists=!fullPackage&&ProjectIdGuard.isSafe(target)&&new File(er.projectDir(target),safe).isFile();if(!inPackage&&!localExists)evidenceIt.remove();}
        ObjectFlowRepository ofr=new ObjectFlowRepository(c);List<String> audioClean=new ArrayList<>();for(String name:objectFlow.consultationAudioFiles){if(!StoredFileNameGuard.isSafeLeaf(name))continue;String safe=name;boolean inPackage=files.contains("flow/audio/"+safe);boolean localExists=!fullPackage&&ProjectIdGuard.isSafe(target)&&new File(ofr.audioDir(target),safe).isFile();if((inPackage||localExists)&&!audioClean.contains(safe))audioClean.add(safe);}objectFlow.consultationAudioFiles.clear();objectFlow.consultationAudioFiles.addAll(audioClean);
    }
    private void reportLight3dPreserved(Context c,String target,SceneSnapshot localScene,SceneSnapshot incoming){String detail="Preserved local point cloud";if(localScene!=null&&incoming!=null)detail+=" localScene="+localScene.updatedAt+" incomingScene="+incoming.updatedAt;new StabilityReporter(c).breadcrumb("LIGHT_3D_PRESERVE",target+" "+detail);}

    private void cleanupFullBinaryState(EvidenceRepository er,ObjectFlowRepository ofr,String target,EvidenceIndex evidence,ObjectFlowState objectFlow)throws IOException{
        Set<String> evidenceKeep=new HashSet<>();for(EvidenceItem item:evidence.items){String n=safeFileName(item.fileName);if(!n.isEmpty())evidenceKeep.add(n);}cleanupDataDir(er.projectDir(target),evidenceKeep,new HashSet<String>(Arrays.asList("index.json")));
        Set<String> audioKeep=new HashSet<>();for(String name:objectFlow.consultationAudioFiles){String n=safeFileName(name);if(!n.isEmpty())audioKeep.add(n);}cleanupDataDir(ofr.audioDir(target),audioKeep,Collections.<String>emptySet());
    }
    private void cleanupDataDir(File dir,Set<String> keep,Set<String> reserved)throws IOException{File[] files=dir.listFiles();if(files==null)return;for(File f:files){if(!f.isFile())continue;String n=f.getName();String base=n.endsWith(".bak")||n.endsWith(".tmp")?n.substring(0,n.length()-4):n;if(reserved.contains(base)||keep.contains(base))continue;if(!f.delete()&&f.exists())throw new IOException("Could not remove stale imported file: "+n);}}

    private void deleteProjectData(Context c,String id){if(!ProjectIdGuard.isSafe(id))return;File files=c.getFilesDir();deleteFileFamily(new File(new File(files,"projects"),id+".json"));deleteFileFamily(new File(new File(files,"floorplans"),id+".json"));deleteFileFamily(new File(new File(files,"commercial"),id+".json"));deleteFileFamily(new File(new File(files,"revisions"),id+".json"));deleteRecursive(new File(new File(files,"scenes"),id));deleteRecursive(new File(new File(files,"fieldwork"),id));deleteRecursive(new File(new File(files,"evidence"),id));deleteRecursive(new File(new File(files,"approvals"),id));deleteRecursive(new File(new File(files,"object_flow"),id));}
    private void deleteFileFamily(File f){if(f==null)return;if(f.exists())f.delete();new File(f.getParentFile(),f.getName()+".bak").delete();new File(f.getParentFile(),f.getName()+".tmp").delete();}
    private void deleteRecursive(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteRecursive(child);}f.delete();}

    private void putBytes(ZipOutputStream z,String name,byte[] data)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(data);z.closeEntry();}
    /** Write a binary payload and prove it is the exact generation hashed into manifest.json.
     * Repositories outside SceneRepository can change while a FULL export is running. Without
     * this check, the manifest could describe generation A while ZIP bytes contain generation B,
     * producing a package that only fails later on import. */
    private void putFileVerified(ZipOutputStream z,String name,File file,String expectedSha)throws Exception{
        if(expectedSha==null||expectedSha.length()!=64)throw new IOException("Missing export checksum: "+name);
        MessageDigest d=MessageDigest.getInstance("SHA-256");boolean opened=false;
        try{
            z.putNextEntry(new ZipEntry(name));opened=true;
            try(InputStream in=new BufferedInputStream(new FileInputStream(file))){byte[] b=new byte[16384];int n;while((n=in.read(b))>0){d.update(b,0,n);z.write(b,0,n);}}
            z.closeEntry();opened=false;
        }catch(Exception e){if(opened)try{z.closeEntry();}catch(Exception ignored){}throw e;}
        String actual=hex(d.digest());
        if(!expectedSha.equalsIgnoreCase(actual))throw new IOException("Export source changed while packaging: "+name);
    }
    private JSONObject json(ImportPayload files,String name)throws Exception{byte[] b=files.bytes(name);if(b==null)throw new IOException("Missing "+name);return new JSONObject(new String(b,"UTF-8"));}
    private String safeEntry(String n){if(n==null||n.length()==0||n.length()>512||n.indexOf('\0')>=0)return null;String x=n.replace('\\','/');if(x.startsWith("/"))return null;String[] parts=x.split("/",-1);for(String part:parts)if(part.isEmpty()||".".equals(part)||"..".equals(part))return null;return x;}
    private String safeFileName(String n){return StoredFileNameGuard.sanitizeLeaf(n);}
    private String sha256(byte[] data)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");return hex(d.digest(data));}
    private String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)d.update(b,0,n);}return hex(d.digest());}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.US,"%02x",x&0xff));return s.toString();}
    private void copy(File from,File to)throws Exception{File parent=to.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create directory");try(InputStream in=new BufferedInputStream(new FileInputStream(from));FileOutputStream fos=new FileOutputStream(to);OutputStream out=new BufferedOutputStream(fos)){byte[] b=new byte[16384];int n;while((n=in.read(b))>0)out.write(b,0,n);out.flush();fos.getFD().sync();}}
    private void writeFileAtomic(File target,File staged)throws Exception{File parent=target.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot create target directory");File tmp=new File(parent,target.getName()+".import");if(tmp.exists())tmp.delete();copy(staged,tmp);AtomicFileStore.replaceBinary(target,tmp);}

    private static final class ImportPayload implements Closeable{
        private final File dir;private final Map<String,byte[]> bytes=new HashMap<>();private final Map<String,File> files=new HashMap<>();private final Map<String,String> sha=new HashMap<>();
        ImportPayload(File dir){this.dir=dir;}
        void addBytes(String name,byte[] value,String digest){bytes.put(name,value);sha.put(name,digest);}
        void addFile(String name,File value,String digest){files.put(name,value);sha.put(name,digest);}
        boolean contains(String name){return bytes.containsKey(name)||files.containsKey(name);}
        byte[] bytes(String name){return bytes.get(name);}
        File file(String name){return files.get(name);}
        String sha(String name){return sha.get(name);}
        Set<String> names(){return sha.keySet();}
        @Override public void close(){delete(dir);}
        private static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] all=f.listFiles();if(all!=null)for(File x:all)delete(x);}f.delete();}
    }
}
