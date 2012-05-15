package ca.donlaidlaw.mongo.webfs.service

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.types.ObjectId;

import sun.tools.tree.UplevelReference;

import com.mongodb.BasicDBObject
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteConcern;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSFile;
import com.mongodb.gridfs.GridFSInputFile;

class FilesystemService {
	public static final String ID = "id";
	public static final String TENANT = "tenant";
	public static final String UPLOADED_BY = "uploadedBy";
	public static final String MODIFIED_BY = "modifiedBy";
	public static final String CLASSIFIER = "classifier";
	public static final String TAGS = "tags";
	public static final String OWNER = "owner";
	public static final String REFERENCES = "references";
	
	DB db
	String bucket = "fs"
	String root = ""
		
    def insertFile(String contentType, InputStream inputStream, Map<String, Object> params) {
		GridFS gridFS = new GridFS(db, bucket)
		String filename = params.name
		GridFSInputFile gridFile = gridFS.createFile(inputStream, filename, false)
		gridFile.setContentType(contentType)
		setFileMetadataFromParams(gridFile, params, true)
		gridFile.save();
		return gridFile;
    }
	
	def updateFile(Map<String, Object> params) {
		GridFS gridFS = new GridFS(db, bucket)
		GridFSDBFile gridFile = gridFS.findOne(new ObjectId(params[ID]))
		if (validateGridFileTenant(gridFile, params[TENANT])) {
			setFileMetadataFromParams(gridFile, params, false)
			gridFile.save();
			return gridFile;
		}
		return null;
	}
	
	def deleteFile(Map<String, Object> params) {
		GridFS gridFS = new GridFS(db, bucket)
		GridFSDBFile gridFile = gridFS.findOne(new ObjectId(params[ID]))
		if (validateGridFileTenant(gridFile, params[TENANT])) {
			gridFS.remove(new ObjectId(params[ID]))
			return gridFile;
		}
		return null;
	}
	
	def setFileMetadataFromParams(GridFSFile file, Map<String, Object> params, boolean isInsert) {
		DBObject metadata = file.getMetaData()
		if (metadata == null) {
			metadata = new BasicDBObject()
			file.setMetaData(metadata)
		}
		metadata.put(TENANT, params[TENANT])
		if (isInsert) {
			if (params[UPLOADED_BY]) metadata.put(UPLOADED_BY, params[UPLOADED_BY])
		} else {
			if (params[MODIFIED_BY]) metadata.put(MODIFIED_BY, params[MODIFIED_BY])
		}
		metadata.put(CLASSIFIER, params[CLASSIFIER])
		metadata.put(OWNER, params[OWNER])
		metadata.put(REFERENCES, params.list(REFERENCES))
		metadata.put(TAGS, params.list(TAGS))
	}
	
	def findDocument(HttpServletRequest request, Map<String, Object> params) {
		GridFS gridFS = new GridFS(db, bucket)	
	}
	
	def getDocument(String tenant, String id) {
		GridFS gridFS = new GridFS(db, bucket)
		GridFSDBFile dbFile = gridFS.findOne(new ObjectId(id))
		if (validateGridFileTenant(dbFile, tenant)) {
			return dbFile;
		}
		return null;
	}
	
	def validateGridFileTenant(GridFSFile file, String tenant) {
		if (tenant == null) {
			// It would be best not to call this method with a null tenant.
			return false;
		}
		if (file) {
			DBObject metadata = file.getMetaData()
			if (metadata) {
				return tenant.equals(metadata[TENANT])
			}
		}
		return false
	}

	def copyMetadataToResponse(GridFSDBFile document, HttpServletResponse response) {
		DBObject metadata = document.getMetaData();
		response.setHeader("WEBFS.ID", document.getId().toString());
		response.setHeader("WEBFS.NAME", document.getFilename());
		response.setHeader("WEBFS.MD5", document.getMD5());
		response.setDateHeader("WEBFS.UPLOAD_DATE", document.getUploadDate().getTime());
		if (metadata) {
			response.setHeader("WEBFS.TENANT", metadata.get(TENANT));
			if (metadata.get(UPLOADED_BY)) response.setHeader("WEBFS.UPLOADED_BY", metadata.get(UPLOADED_BY))
			if (metadata.get(MODIFIED_BY)) response.setHeader("WEBFS.MODIFIED_BY", metadata.get(MODIFIED_BY))
			if (metadata.get(CLASSIFIER)) response.setHeader("WEBFS.CLASSIFIER", metadata.get(CLASSIFIER))
			if (metadata.get(OWNER)) response.setHeader("WEBFS.OWNER", metadata.get(OWNER))
			if (metadata.get(REFERENCES)) {
				if (metadata.get(REFERENCES).getClass().isArray()) {
					metadata.get(REFERENCES).each { ref ->
						response.addHeader("WEBFS.REFERENCES", ref)
					}
				} else {
					response.setHeader("WEBFS.REFERENCES", metadata.get(REFERENCES))
				}
			}
			if (metadata.get(TAGS)) {
				if (metadata.get(TAGS).getClass().isArray() || Collection.class.isInstance(metadata.get(TAGS))) {
					metadata.get(TAGS).each { tag ->
						response.addHeader("WEBFS.TAGS", tag)
					}
				} else {
					response.setHeader("WEBFS.TAGS", metadata.get(TAGS))
				}
			}
		}
	}
	
	/*
	 * We will not track versions of files with an auto-increment counter. Versions
	 * will be derived from insert order only. So we can think of the _id of the
	 * file to be a version identifier. The _id is only accurate to the nearest second
	 * for ordering, so maybe use the uploaded date/time instead.
	def getNextFileVersion(String fileName) {
		DBCollection coll = db.getCollection("${bucket}.versions")
		DBObject query = new BasicDBObject("_id", fileName)
		DBObject sort = null
		DBObject fields = null
		DBObject update = new BasicDBObject("\$inc", new BasicDBObject("version", new Long(1)))
//		findAndModify(com.mongodb.DBObject, com.mongodb.DBObject, com.mongodb.DBObject, boolean, com.mongodb.DBObject, boolean, boolean)
//		def result = coll.findAndModify(query, fields, sort, Boolean.FALSE, update, Boolean.TRUE, Boolean.TRUE)
		def result = coll.findAndModify(query, update)
		if (result == null) {
			query.put("version", new Long(1))
			coll.insert(query, WriteConcern.SAFE)
			return 0L
		}
		return result.version;
	}
	*/
	
	/*
	 * We are not trying to emulate a computer filesystem. There are no normalized
	 * file names here. Files may have any string for a name, the interpretation of
	 * a hierarchy, or any other structure of files is up to the user.
	String makeGridFSFilename(String name) {
		String filename = name
		if (!filename.startsWith("/")) filename = root + "/" + filename
		else filename = root + filename
		return filename
	}
	*/
}
