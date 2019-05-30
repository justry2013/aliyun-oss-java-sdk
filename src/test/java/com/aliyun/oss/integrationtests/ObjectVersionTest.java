/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.aliyun.oss.integrationtests;

import static com.aliyun.oss.integrationtests.TestUtils.calcMultipartsETag;
import static com.aliyun.oss.integrationtests.TestUtils.claimUploadId;
import static com.aliyun.oss.integrationtests.TestUtils.genFixedLengthInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import com.aliyun.oss.OSSErrorCode;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadResult;
import com.aliyun.oss.model.CopyObjectRequest;
import com.aliyun.oss.model.CopyObjectResult;
import com.aliyun.oss.model.DeleteVersionsRequest;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSSymlink;
import com.aliyun.oss.model.OSSVersionSummary;
import com.aliyun.oss.model.ObjectAcl;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ObjectPermission;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.RestoreObjectResult;
import com.aliyun.oss.model.SetObjectAclRequest;
import com.aliyun.oss.model.SetObjectTaggingRequest;
import com.aliyun.oss.model.SimplifiedObjectMeta;
import com.aliyun.oss.model.TagSet;
import com.aliyun.oss.model.UploadPartCopyRequest;
import com.aliyun.oss.model.UploadPartCopyResult;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.aliyun.oss.model.VersionListing;
import com.aliyun.oss.model.DeleteVersionsRequest.KeyVersion;
import com.aliyun.oss.model.DeleteVersionsResult;
import com.aliyun.oss.model.GenericRequest;

public class ObjectVersionTest extends TestBase {

    @Test
    public void testPutObject() {
        String key = "version-test-put-object";
        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            PutObjectResult putResult = ossClient.putObject(bucketName, key, instream, metadata);
            Assert.assertNotNull(putResult.getVersionId());
            Assert.assertEquals(64, putResult.getVersionId().length());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testMultipartUpload() {
        String key = "version-test-upload-multiparts";
        int partSize = 128 * 1024; // 128KB
        int partCount = 3;

        try {
            // Initial multipart upload
            String uploadId = claimUploadId(ossClient, bucketName, key);

            // Upload parts
            List<PartETag> partETags = new ArrayList<PartETag>();
            for (int i = 0; i < partCount; i++) {
                InputStream instream = genFixedLengthInputStream(partSize);
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(bucketName);
                uploadPartRequest.setKey(key);
                uploadPartRequest.setInputStream(instream);
                uploadPartRequest.setPartNumber(i + 1);
                uploadPartRequest.setPartSize(partSize);
                uploadPartRequest.setUploadId(uploadId);
                UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                Assert.assertEquals(uploadPartResult.getRequestId().length(), REQUEST_ID_LEN);
                partETags.add(uploadPartResult.getPartETag());
            }

            // Complete multipart upload
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucketName, key, uploadId, partETags);
            CompleteMultipartUploadResult completeMultipartUploadResult =
                ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            Assert.assertEquals(bucketName, completeMultipartUploadResult.getBucketName());
            Assert.assertEquals(key, completeMultipartUploadResult.getKey());
            Assert.assertEquals(calcMultipartsETag(partETags), completeMultipartUploadResult.getETag());
            Assert.assertEquals(completeMultipartUploadResult.getRequestId().length(), REQUEST_ID_LEN);
            Assert.assertNotNull(completeMultipartUploadResult.getVersionId());
            Assert.assertEquals(64, completeMultipartUploadResult.getVersionId().length());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testGetObject() {
        String key = "version-test-get-object";
        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            PutObjectResult putResult = ossClient.putObject(bucketName, key, instream, metadata);
            Assert.assertTrue(putResult.getVersionId() != null);
            Assert.assertTrue(putResult.getVersionId().length() == 64);

            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);
            getObjectRequest.setVersionId(putResult.getVersionId());
            OSSObject ossObject = ossClient.getObject(getObjectRequest);
            ossObject.close();
            Assert.assertEquals(inputStreamLength, ossObject.getObjectMetadata().getContentLength());
            Assert.assertEquals(putResult.getVersionId(), ossObject.getObjectMetadata().getVersionId());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void tesDeleteVersion() {
        String key = "version-test-del-version";
        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            PutObjectResult putResult = ossClient.putObject(bucketName, key, instream, metadata);
            Assert.assertTrue(putResult.getVersionId() != null);
            Assert.assertTrue(putResult.getVersionId().length() == 64);

            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, key);
            getObjectRequest.setVersionId(putResult.getVersionId());
            OSSObject ossObject = ossClient.getObject(getObjectRequest);
            ossObject.close();
            Assert.assertEquals(inputStreamLength, ossObject.getObjectMetadata().getContentLength());
            Assert.assertEquals(putResult.getVersionId(), ossObject.getObjectMetadata().getVersionId());

            ossClient.deleteVersion(bucketName, key, putResult.getVersionId());

            try {
                ossClient.getObject(getObjectRequest);
            } catch (OSSException e) {
                Assert.assertEquals(OSSErrorCode.NO_SUCH_VERSION, e.getErrorCode());
            }
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void tesDeleteVersions() {
        String prefix = "version-test-del-multiple-versions";
        long inputStreamLength = 1024;
        String key = null;

        try {
            // Prepare
            InputStream instream = genFixedLengthInputStream(inputStreamLength);

            key = prefix + "-1";
            ossClient.putObject(bucketName, key, instream);
            ossClient.putObject(bucketName, key, instream);

            key = prefix + "-2";
            ossClient.putObject(bucketName, key, instream);
            ossClient.deleteObject(bucketName, key);

            VersionListing versionListing = ossClient.listVersions(bucketName, prefix);
            Assert.assertEquals(4, versionListing.getVersionSummaries().size());

            List<KeyVersion> keysTodel = new ArrayList<KeyVersion>();
            for (OSSVersionSummary ossVersion : versionListing.getVersionSummaries()) {
                keysTodel.add(new KeyVersion(ossVersion.getKey(), ossVersion.getVersionId()));
            }

            // Delete versions
            DeleteVersionsRequest delRequest = new DeleteVersionsRequest(bucketName);
            delRequest.setKeys(keysTodel);
            DeleteVersionsResult delRes = ossClient.deleteVersions(delRequest);
            Assert.assertEquals(4, delRes.getDeletedVersions().size());

            // Check
            versionListing = ossClient.listVersions(bucketName, prefix);
            Assert.assertEquals(0, versionListing.getVersionSummaries().size());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testSymlink() {
        String link = "version-test-symlink";
        String target = "version-test-symlink";
        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            ossClient.putObject(bucketName, target, instream, metadata);

            ossClient.createSymlink(bucketName, link, target);

            OSSSymlink symlink = ossClient.getSymlink(bucketName, link);
            Assert.assertEquals(64, symlink.getMetadata().getVersionId().length());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testCopyObject() {
        String srcKey = "version-test-src";
        String destKey = "version-test-dest";

        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            PutObjectResult puObjectResult = ossClient.putObject(bucketName, srcKey, instream, metadata);

            CopyObjectRequest copyObjectRequest = new CopyObjectRequest(bucketName, srcKey, bucketName, destKey);
            copyObjectRequest.setSourceVersionId(puObjectResult.getVersionId());
            CopyObjectResult copyObjectResult = ossClient.copyObject(copyObjectRequest);
            Assert.assertTrue(copyObjectResult.getVersionId() != null);
            Assert.assertTrue(copyObjectResult.getVersionId().length() == 64);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testUploadPartCopy() {
        String srcKey = "version-test-upload-part-copy-src";
        String destKey = "version-test-upload-part-copy-dest";
        long partSize = 128 * 1024;

        try {
            // Prepare
            InputStream instream = genFixedLengthInputStream(partSize);
            PutObjectResult putObjectResult = ossClient.putObject(bucketName, srcKey, instream);
            Assert.assertNotNull(putObjectResult.getVersionId());
            Assert.assertEquals(64, putObjectResult.getVersionId().length());

            // Claim upload id
            String uploadId = claimUploadId(ossClient, bucketName, destKey);

            // Upload part copy
            int partNumber = 1;
            List<PartETag> partETags = new ArrayList<PartETag>();
            UploadPartCopyRequest uploadPartCopyRequest =
                new UploadPartCopyRequest(bucketName, srcKey, bucketName, destKey);
            uploadPartCopyRequest.setSourceVersionId(putObjectResult.getVersionId());
            uploadPartCopyRequest.setPartNumber(partNumber);
            uploadPartCopyRequest.setUploadId(uploadId);
            UploadPartCopyResult uploadPartCopyResult = ossClient.uploadPartCopy(uploadPartCopyRequest);
            partETags.add(uploadPartCopyResult.getPartETag());

            // Complete multipart upload
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucketName, destKey, uploadId, partETags);
            CompleteMultipartUploadResult completeMultipartUploadResult =
                ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            Assert.assertEquals(bucketName, completeMultipartUploadResult.getBucketName());
            Assert.assertEquals(destKey, completeMultipartUploadResult.getKey());
            Assert.assertEquals(calcMultipartsETag(partETags), completeMultipartUploadResult.getETag());
            Assert.assertEquals(completeMultipartUploadResult.getRequestId().length(), REQUEST_ID_LEN);
            Assert.assertNotNull(completeMultipartUploadResult.getVersionId());
            Assert.assertEquals(64, completeMultipartUploadResult.getVersionId().length());

            // Get uploaded object
            OSSObject ossObject = ossClient.getObject(bucketName, destKey);
            Assert.assertEquals(partSize, ossObject.getObjectMetadata().getContentLength());
            Assert.assertEquals(calcMultipartsETag(partETags), ossObject.getObjectMetadata().getETag());
            Assert.assertEquals(ossObject.getRequestId().length(), REQUEST_ID_LEN);
            Assert.assertEquals(completeMultipartUploadResult.getVersionId(),
                ossObject.getObjectMetadata().getVersionId());
            ossObject.close();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testGetObjectMetadata() {
        String key = "version-test-get-object-metadata";
        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            PutObjectResult putResult = ossClient.putObject(bucketName, key, instream, metadata);
            Assert.assertNotNull(putResult.getVersionId());
            Assert.assertEquals(64, putResult.getVersionId().length());

            GenericRequest getObjectMetadataRequest = new GenericRequest(bucketName, key, putResult.getVersionId());
            ObjectMetadata objectMetadata = ossClient.getObjectMetadata(getObjectMetadataRequest);
            Assert.assertNotNull(objectMetadata.getVersionId());
            Assert.assertEquals(64, objectMetadata.getVersionId().length());

            GenericRequest getSimplifiedObjectMetaRequest =
                new GenericRequest(bucketName, key, putResult.getVersionId());
            SimplifiedObjectMeta simplifiedObjectMeta =
                ossClient.getSimplifiedObjectMeta(getSimplifiedObjectMetaRequest);
            Assert.assertNotNull(simplifiedObjectMeta.getVersionId());
            Assert.assertEquals(64, simplifiedObjectMeta.getVersionId().length());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testObjectAcl() {
        String key = "version-test-set-object-acl";
        long inputStreamLength = 1024;

        try {
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();

            PutObjectResult putResult = ossClient.putObject(bucketName, key, instream, metadata);
            Assert.assertNotNull(putResult.getVersionId());
            Assert.assertEquals(64, putResult.getVersionId().length());

            SetObjectAclRequest setObjectAclRequest = new SetObjectAclRequest(bucketName, key,
                putResult.getVersionId(), CannedAccessControlList.Private);
            ossClient.setObjectAcl(setObjectAclRequest);

            GenericRequest genericRequest = new GenericRequest(bucketName, key, putResult.getVersionId());
            ObjectAcl objectAcl = ossClient.getObjectAcl(genericRequest);
            Assert.assertEquals(ObjectPermission.Private, objectAcl.getPermission());
            Assert.assertNotNull(objectAcl.getVersionId());
            Assert.assertEquals(64, objectAcl.getVersionId().length());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testRestoreObject() {
        String key = "version-test-restore-object";
        long inputStreamLength = 1024;

        try {
            // put
            InputStream instream = genFixedLengthInputStream(inputStreamLength);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setHeader("x-oss-storage-class", "Archive");

            PutObjectResult putResult = ossClient.putObject(bucketName, key, instream, metadata);
            Assert.assertNotNull(putResult.getVersionId());
            Assert.assertEquals(64, putResult.getVersionId().length());

            // restore
            GenericRequest genericRequest = new GenericRequest(bucketName, key, putResult.getVersionId());
            RestoreObjectResult restoreObjectResult = ossClient.restoreObject(genericRequest);
            Assert.assertEquals(202, restoreObjectResult.getStatusCode());
            Assert.assertNotNull(restoreObjectResult.getVersionId());
            Assert.assertEquals(64, restoreObjectResult.getVersionId().length());
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testObjectTagging() {
        String key = "version-test-object-tagging";

        try {
            InputStream instream = genFixedLengthInputStream(1024);
            PutObjectResult putObjectResult = ossClient.putObject(bucketName, key, instream);
            String versionId = putObjectResult.getVersionId();

            Map<String, String> tags = new HashMap<String, String>(1);
            tags.put("tag1", "balabala");
            tags.put("tag2", "haha");

            SetObjectTaggingRequest setObjectTaggingRequest = new SetObjectTaggingRequest(bucketName, key, tags);
            setObjectTaggingRequest.setVersionId(versionId);
            ossClient.setObjectTagging(setObjectTaggingRequest);

            GenericRequest genericRequest = new GenericRequest(bucketName, key, versionId);
            TagSet tagSet = ossClient.getObjectTagging(genericRequest);
            Assert.assertEquals(tagSet.getAllTags().size(), 2);

            ossClient.deleteObjectTagging(genericRequest);

            tagSet = ossClient.getObjectTagging(genericRequest);
            Assert.assertEquals(tagSet.getAllTags().size(), 0);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

}
