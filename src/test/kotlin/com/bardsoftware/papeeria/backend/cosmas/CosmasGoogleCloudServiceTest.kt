/**
Copyright 2018 BarD Software s.r.o

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.bardsoftware.papeeria.backend.cosmas

import com.bardsoftware.papeeria.backend.cosmas.CosmasProto.*
import com.google.api.gax.paging.Page
import com.google.cloud.storage.*
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import com.google.protobuf.ByteString
import io.grpc.internal.testing.StreamRecorder
import name.fraser.neil.plaintext.diff_match_patch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Matchers.any
import org.mockito.Matchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*


/**
 * Some tests for CosmasGoogleCloudService
 *
 * @author Aleksandr Fedotov (iisuslik43)
 */
class CosmasGoogleCloudServiceTest {

    private val BUCKET_NAME = "papeeria-interns-cosmas"
    private var service = getServiceForTests()
    private val dmp = diff_match_patch()

    @Before
    fun testInitialization() {
        this.service = getServiceForTests()
        println()
    }

    @Test
    fun addFileAndGetFile() {
        createVersion("file", "43")
        commit()
        val file = getFileFromService(0, "43")
        assertEquals("file", file)
        this.service.deleteFile("43")
        val (stream, request) = getStreamRecorderAndRequestForGettingVersion(0, "43")
        this.service.getVersion(request, stream)
        assertEquals(0, stream.values.size)
        assertNotNull(stream.error)
    }

    @Test
    fun getFileThatNotExists() {
        val (stream, request) = getStreamRecorderAndRequestForGettingVersion(0, "43")
        this.service.getVersion(request, stream)
        assertEquals(0, stream.values.size)
        assertNotNull(stream.error)
    }

    @Test
    fun addTwoFiles() {
        createVersion("file1", "1")
        createVersion("file2", "2")
        commit()
        val file1 = getFileFromService(0, "1")
        val file2 = getFileFromService(0, "2")
        assertEquals("file1", file1)
        assertEquals("file2", file2)
        this.service.deleteFile("1")
        this.service.deleteFile("2")
        val (stream1, request1) = getStreamRecorderAndRequestForGettingVersion(0, "1")
        this.service.getVersion(request1, stream1)
        assertEquals(0, stream1.values.size)
        assertNotNull(stream1.error)
        val (stream2, request2) = getStreamRecorderAndRequestForGettingVersion(0, "2")
        this.service.getVersion(request2, stream2)
        assertEquals(0, stream2.values.size)
        assertNotNull(stream2.error)
    }

    @Test
    fun addSecondVersionAndCheckListVersions() {
        val fakeStorage: Storage = mock(Storage::class.java)
        val blob1 = getMockedBlob("ver1", 1)
        val blob2 = getMockedBlob("ver2", 2)
        val fakePage: Page<Blob> = mock(Page::class.java) as Page<Blob>

        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), any(ByteArray::class.java)))
                .thenReturn(blob1).thenReturn(blob2)

        Mockito.`when`(fakeStorage.list(eq(this.BUCKET_NAME),
                any(Storage.BlobListOption::class.java), any(Storage.BlobListOption::class.java)))
                .thenReturn(fakePage)

        Mockito.`when`(fakePage.iterateAll())
                .thenReturn(listOf(blob1, blob2))
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        createVersion("ver1", "43")
        createVersion("ver2", "43")
        commit()
        assertEquals(listOf(1L, 2L), getVersionsList("43"))
    }

    @Test
    fun getBothVersions() {
        val fakeStorage: Storage = mock(Storage::class.java)
        val blob1 = getMockedBlob("ver1", 1444)
        val blob2 = getMockedBlob("ver2", 822)
        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), any(ByteArray::class.java)))
                .thenReturn(blob1).thenReturn(blob2)
        Mockito.`when`(fakeStorage.get(
                any(BlobId::class.java), any(Storage.BlobGetOption::class.java)))
                .thenReturn(blob1).thenReturn(blob2)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        createVersion("ver1", "43")
        commit()
        createVersion("ver2", "43")
        commit()
        assertEquals("ver1", getFileFromService(1444, "43"))
        assertEquals("ver2", getFileFromService(822, "43"))
    }

    @Test
    fun handleStorageException() {
        val fakeStorage: Storage = mock(Storage::class.java)
        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), any(ByteArray::class.java)))
                .thenThrow(StorageException(1, "test"))
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        createVersion("file")
        val (commitRecorder, commitRequest) = getStreamRecorderAndRequestForCommitVersions()
        this.service.commitVersion(commitRequest, commitRecorder)
        assertNotNull(commitRecorder.error)
        assertEquals("test", commitRecorder.error!!.message)
    }

    @Test
    fun addTwoVersionsAndOneCommit() {
        createVersion("ver0")
        createVersion("ver1")
        commit()
        assertEquals("ver1", getFileFromService(0))
    }

    @Test
    fun makeTwoCommits() {
        val fakeStorage: Storage = mock(Storage::class.java)
        val blob1 = getMockedBlob("ver2", 0)
        val blob2 = getMockedBlob("ver4", 1)
        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), eq("ver2".toByteArray())))
                .thenReturn(blob1)
        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), eq("ver4".toByteArray())))
                .thenReturn(blob2)
        Mockito.`when`(fakeStorage.get(
                any(BlobId::class.java), any(Storage.BlobGetOption::class.java)))
                .thenReturn(blob1).thenReturn(blob2)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        createVersion("ver1")
        createVersion("ver2")
        commit()
        createVersion("ver3")
        createVersion("ver4")
        commit()
        assertEquals("ver2", getFileFromService(0))
        assertEquals("ver4", getFileFromService(1))
        verify(fakeStorage, never()).create(any(BlobInfo::class.java), eq("ver1".toByteArray()))
        verify(fakeStorage, never()).create(any(BlobInfo::class.java), eq("ver3".toByteArray()))
    }

    @Test
    fun addTwoFilesFromOneProject() {
        createVersion("file0", "0")
        createVersion("file1", "1")
        commit()
        assertEquals("file0", getFileFromService(0, "0"))
        assertEquals("file1", getFileFromService(0, "1"))
    }

    @Test
    fun addTwoFilesFromDifferentProjects() {
        createVersion("file0", "0", "0")
        createVersion("file1", "1", "1")
        commit("0")
        assertEquals("file0", getFileFromService(0, "0"))
        val (stream, request) = getStreamRecorderAndRequestForGettingVersion(0, "1", "1")
        this.service.getVersion(request, stream)
        assertNotNull(stream.error)
    }

    @Test
    fun simpleAddPatch() {
        createVersion("file", "1", "1")
        addPatchToService("abc", "-", "1", 1, "1")
        val list = this.service.getPatchList("1", "1")
        assertNotNull(list)
        assertEquals(1, list!!.size)
        assertEquals("abc", list[0].text)
        assertEquals("-", list[0].userId)
        assertEquals(1L, list[0].timestamp)
        commit("1")
        val listNull = this.service.getPatchList("1", "1")
        assertNull(listNull)
        createVersion("fileNew", "1", "1")
        addPatchToService("abcNew", "-New", "1", 10, "1")
        val list1 = this.service.getPatchList("1", "1")
        assertNotNull(list1)
        assertEquals(1, list1!!.size)
        assertEquals("abcNew", list1[0].text)
        assertEquals("-New", list1[0].userId)
        assertEquals(10L, list1[0].timestamp)
    }

    @Test
    fun checkEmptyList() {
        createVersion("file", "1", "1")
        addPatchToService("abc", "-", "1", 1, "1")
        commit("1")
        val listNull = this.service.getPatchList("1", "1")
        assertNull(listNull)
        createVersion("file1", "1", "1")
        val list = this.service.getPatchList("1", "1")
        assertEquals(0, list!!.size)
    }

    @Test
    fun addPatchTest() {
        val listPatch1 = mutableListOf<Patch>()
        val listPatch2 = mutableListOf<Patch>()
        val patch1 = newPatch("-1", "patch1", 1)
        val patch2 = newPatch("-2", "patch2", 2)
        val patch3 = newPatch("-3", "patch3", 3)
        val patch4 = newPatch("-4", "patch4", 4)
        listPatch1.add(patch1)
        listPatch1.add(patch2)
        listPatch2.add(patch3)
        listPatch2.add(patch4)
        val fileVersion1 = CosmasProto.FileVersion.newBuilder()
                .setContent(ByteString.copyFrom("ver1".toByteArray()))
                .addAllPatches(listPatch1)
                .build()
        val fileVersion2 = CosmasProto.FileVersion.newBuilder()
                .setContent(ByteString.copyFrom("ver2".toByteArray()))
                .addAllPatches(listPatch2)
                .build()
        val fakeStorage: Storage = mock(Storage::class.java)
        val blob1 = getMockedBlobWithPatch("ver1", 0, listPatch1)
        val blob2 = getMockedBlobWithPatch("ver2", 1, listPatch2)
        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), eq(fileVersion1.toByteArray())))
                .thenReturn(blob1)
        Mockito.`when`(fakeStorage.create(
                any(BlobInfo::class.java), eq(fileVersion2.toByteArray())))
                .thenReturn(blob2)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        createVersion("ver1", "1", "1")
        addPatchToService("patch1", "-1", "1", 1, "1")
        addPatchToService("patch2", "-2", "1", 2, "1")
        commit("1")
        createVersion("ver2", "1", "1")
        addPatchToService("patch3", "-3", "3", 3, "1")
        addPatchToService("patch4", "-4", "4", 4, "1")
        commit("1")
    }

    @Test
    fun getPatchSimple() {
        val listPatch1 = mutableListOf<Patch>()
        val patch1 = newPatch("-1", "patch1", 1)
        val patch2 = newPatch("-2", "patch2", 2)
        listPatch1.add(patch1)
        listPatch1.add(patch2)
        val fakeStorage: Storage = mock(Storage::class.java)
        val blob1 = getMockedBlobWithPatch("ver1", 1444, listPatch1)
        Mockito.`when`(fakeStorage.get(
                any(BlobId::class.java), any(Storage.BlobGetOption::class.java)))
                .thenReturn(blob1)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        val list = this.service.getPatchListFromStorage("1", 0)
        assertEquals(2, list!!.size)
        assertEquals(newPatch("-1", "patch1", 1), list[0])
        assertEquals(newPatch("-2", "patch2", 2), list[1])
    }

    @Test
    fun getTextWithoutPatchSimple() {
        val text1 = "Hello"
        val text2 = "Hello world"
        val text3 = "Hello beautiful world"
        val text4 = "Hello beautiful life"
        val patch1 = newPatch("-", dmp.patch_toText(dmp.patch_make(text1, text2)), 1)
        val patch2 = newPatch("-", dmp.patch_toText(dmp.patch_make(text2, text3)), 2)
        val patch3 = newPatch("-", dmp.patch_toText(dmp.patch_make(text3, text4)), 3)
        val listPatch = mutableListOf(patch1, patch2, patch3)
        val blob0 = getMockedBlobWithPatch(text1, 900, mutableListOf())
        val blob1 = getMockedBlobWithPatch(text4, 1444, listPatch)
        val fakeStorage: Storage = mock(Storage::class.java)
        val fakePage: Page<Blob> = mock(Page::class.java) as Page<Blob>
        Mockito.`when`(fakePage.iterateAll())
                .thenReturn(listOf(blob1, blob0))
        Mockito.`when`(fakeStorage.list(eq(this.BUCKET_NAME),
                any(Storage.BlobListOption::class.java), any(Storage.BlobListOption::class.java)))
                .thenReturn(fakePage)
        val generation = 43L
        val fileId = "1"
        Mockito.`when`(fakeStorage.get(BlobId.of(this.BUCKET_NAME, fileId, generation))).thenReturn(blob1)
        Mockito.`when`(fakeStorage.get(BlobId.of(this.BUCKET_NAME, fileId))).thenReturn(blob1)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        val resultText = deletePatch(fileId, "1", generation, 2)
        assertEquals("Hello life", resultText)
    }

    @Test
    fun deletePatchLongList() {
        val text1 = """Mr and Mrs Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal,
              | thank you very much. They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense.""".trimMargin().replace("\n", "")
        val text2 = """Mr and Mrs Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal,
              | thank you very much. They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr. Dursley was the director of a firm called Grunnings,
              | which made drills.""".trimMargin().replace("\n", "")
        val text3 = """Mr and Mrs Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr. Dursley was the director of a firm called Grunnings,
              | which made drills.""".trimMargin().replace("\n", "")
        val text4 = """Mr Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr. Dursley was the director of a firm called Grunnings,
              | which made drills.""".trimMargin().replace("\n", "")
        val text5 = """Mr Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Grunnings,
              | which made drills.""".trimMargin().replace("\n", "")
        val text6 = """Mr Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Grunnings,
              | which made furniture.""".trimMargin().replace("\n", "")
        val text7 = """Mr Dursley, of number six, Privet Drive, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Grunnings,
              | which made furniture.""".trimMargin().replace("\n", "")
        val text8 = """Mr Dursley, of number six, Wall Street, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Grunnings,
              | which made furniture.""".trimMargin().replace("\n", "")
        val text9 = """Mr Dursley, of number six, Wall Street, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange or mysterious,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Happy,
              | which made furniture.""".trimMargin().replace("\n", "")
        val text10 = """Mr Dursley, of number six, Wall Street, were proud to say that they were perfectly normal.
              | They were the last people you'd expect to be involved in anything strange,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Happy,
              | which made furniture.""".trimMargin().replace("\n", "")
        val text11 = """Mr Dursley, of number six, Wall Street, were proud to say that they were perfectly strange.
              | They were the last people you'd expect to be involved in anything normal,
              | because they just didn't hold with such nonsense. Mr Dursley was the director of a firm called Happy,
              | which made furniture.""".trimMargin().replace("\n", "")
        val patch1 = newPatch("-", dmp.patch_toText(dmp.patch_make(text1, text2)), 1)
        val patch2 = newPatch("-", dmp.patch_toText(dmp.patch_make(text2, text3)), 2)
        val patch3 = newPatch("-", dmp.patch_toText(dmp.patch_make(text3, text4)), 3)
        val patch4 = newPatch("-", dmp.patch_toText(dmp.patch_make(text4, text5)), 4)
        val patch5 = newPatch("-", dmp.patch_toText(dmp.patch_make(text5, text6)), 5)
        val patch6 = newPatch("-", dmp.patch_toText(dmp.patch_make(text6, text7)), 6)
        val patch7 = newPatch("-", dmp.patch_toText(dmp.patch_make(text7, text8)), 7)
        val patch8 = newPatch("-", dmp.patch_toText(dmp.patch_make(text8, text9)), 8)
        val patch9 = newPatch("-", dmp.patch_toText(dmp.patch_make(text9, text10)), 9)
        val patch10 = newPatch("-", dmp.patch_toText(dmp.patch_make(text10, text11)), 10)
        val listPatch = mutableListOf<Patch>()
        listPatch.add(patch1)
        listPatch.add(patch2)
        listPatch.add(patch3)
        listPatch.add(patch4)
        listPatch.add(patch5)
        listPatch.add(patch6)
        listPatch.add(patch7)
        listPatch.add(patch8)
        listPatch.add(patch9)
        listPatch.add(patch10)
        val blob0 = getMockedBlobWithPatch(text1, 900, mutableListOf())
        val blob1 = getMockedBlobWithPatch(text11, 1444, listPatch)
        val fakeStorage: Storage = mock(Storage::class.java)
        val fakePage: Page<Blob> = mock(Page::class.java) as Page<Blob>
        Mockito.`when`(fakePage.iterateAll())
                .thenReturn(listOf(blob1, blob0))
        Mockito.`when`(fakeStorage.list(eq(this.BUCKET_NAME),
                any(Storage.BlobListOption::class.java), any(Storage.BlobListOption::class.java)))
                .thenReturn(fakePage)
        val generation = 43L
        val fileId = "1"
        Mockito.`when`(fakeStorage.get(BlobId.of(this.BUCKET_NAME, fileId, generation))).thenReturn(blob1)
        Mockito.`when`(fakeStorage.get(BlobId.of(this.BUCKET_NAME, fileId))).thenReturn(blob1)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        val resultText = deletePatch(fileId, "1", generation, 4)
        assertEquals("""Mr Dursley, of number six, Wall Street, were proud to say that they were perfectly strange.
              | They were the last people you'd expect to be involved in anything normal,
              | because they just didn't hold with such nonsense. Mr. Dursley was the director of a firm called Happy,
              | which made furniture.""".trimMargin().replace("\n", ""), resultText)
    }

    @Test
    fun getTextWithoutPatchFileVersions() {
        val text1 = """Not for the first time, an argument had broken out over breakfast at number four,
            | Privet Drive. Mr. Vernon Dursley had been woken in the early hours of the morning by a loud,
            | hooting noise from his nephew Harry's room.""".trimMargin().replace("\n", "")
        val text2 = """Not for the first time, an argument had broken out over breakfast at number four,
            | Wall Street. Mr. Vernon Dursley had been woken in the early hours of the morning by a loud,
            | hooting noise from his nephew Harry's room.""".trimMargin().replace("\n", "")
        val text3 = """Not for the first time, an argument had broken out over breakfast at number four,
            | Wall Street. Mr. Dursley had been woken in the early hours of the morning by a loud,
            | hooting noise from his nephew Harry's room.""".trimMargin().replace("\n", "")
        val text4 = """Not for the first time, an argument had broken out over breakfast at number four,
            | Wall Street. Mr. Dursley had been woken in the early hours of the morning by a loud,
            | hooting noise from his nephew's room.""".trimMargin().replace("\n", "")
        val text5 = """Not for the first time, an argument had broken out over breakfast at number four,
            | Wall Street. Mr. Braun had been woken in the early hours of the morning by a loud,
            | hooting noise from his nephew's room.""".trimMargin().replace("\n", "")
        val patch1 = newPatch("-", dmp.patch_toText(dmp.patch_make(text1, text2)), 1)
        val patch2 = newPatch("-", dmp.patch_toText(dmp.patch_make(text2, text3)), 2)
        val patch3 = newPatch("-", dmp.patch_toText(dmp.patch_make(text3, text4)), 3)
        val patch4 = newPatch("-", dmp.patch_toText(dmp.patch_make(text4, text5)), 4)
        val blob0 = getMockedBlobWithPatch(text1, 200, mutableListOf())
        val blob1 = getMockedBlobWithPatch(text2, 300, mutableListOf(patch1))
        val blob2 = getMockedBlobWithPatch(text3, 400, mutableListOf(patch2))
        val blob3 = getMockedBlobWithPatch(text4, 500, mutableListOf(patch3))
        val blob4 = getMockedBlobWithPatch(text5, 600, mutableListOf(patch4))
        val fakeStorage: Storage = mock(Storage::class.java)
        val fakePage: Page<Blob> = mock(Page::class.java) as Page<Blob>
        Mockito.`when`(fakePage.iterateAll())
                .thenReturn(listOf(blob1, blob0, blob2, blob3, blob4))
        Mockito.`when`(fakeStorage.list(eq(this.BUCKET_NAME),
                any(Storage.BlobListOption::class.java), any(Storage.BlobListOption::class.java)))
                .thenReturn(fakePage)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        val generation = 43L
        val fileId = "1"
        Mockito.`when`(fakeStorage.get(BlobId.of(this.BUCKET_NAME, fileId, generation))).thenReturn(blob1)
        Mockito.`when`(fakeStorage.get(BlobId.of(this.BUCKET_NAME, fileId))).thenReturn(blob4)
        val resultText = deletePatch(fileId, "1", generation, 1)
        assertEquals("""Not for the first time, an argument had broken out over breakfast at number four,
            | Privet Drive. Mr. Braun had been woken in the early hours of the morning by a loud,
            | hooting noise from his nephew's room.""".trimMargin().replace("\n", ""), resultText)
    }

    @Test
    fun getPatchManyVersions() {
        val listPatch1 = mutableListOf<Patch>()
        val listPatch2 = mutableListOf<Patch>()
        val patch1 = newPatch("-1", "patch1", 1)
        val patch2 = newPatch("-2", "patch2", 2)
        val patch3 = newPatch("-3", "patch3", 3)
        val patch4 = newPatch("-4", "patch4", 4)
        listPatch1.add(patch1)
        listPatch1.add(patch2)
        listPatch2.add(patch3)
        listPatch2.add(patch4)
        val fakeStorage: Storage = mock(Storage::class.java)
        val blob1 = getMockedBlobWithPatch("ver1", 1444, listPatch1)
        val blob2 = getMockedBlobWithPatch("ver2", 822, listPatch2)
        Mockito.`when`(fakeStorage.get(
                any(BlobId::class.java), any(Storage.BlobGetOption::class.java)))
                .thenReturn(blob1).thenReturn(blob2)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        val list1 = this.service.getPatchListFromStorage("1", 0)
        val list2 = this.service.getPatchListFromStorage("1", 1)
        assertEquals(2, list1!!.size)
        assertEquals(newPatch("-1", "patch1", 1), list1[0])
        assertEquals(newPatch("-2", "patch2", 2), list1[1])
        assertEquals(newPatch("-3", "patch3", 3), list2!![0])
        assertEquals(newPatch("-4", "patch4", 4), list2[1])
    }

    @Test
    fun deleteFile() {
        val fakeStorage = mock(Storage::class.java)
        val projectId = "1"
        val fileId = "1"
        val time = 1L
        val cemetery = FileCemetery.newBuilder().addAllCemetery(mutableListOf()).build()
        val cemeteryBlob = getMockedBlobWithCemetery(cemetery)
        Mockito.`when`(fakeStorage.get(eq(BlobId.of(this.BUCKET_NAME, projectId)))).thenReturn(cemeteryBlob)
        this.service = CosmasGoogleCloudService(this.BUCKET_NAME, fakeStorage)
        val streamRecorder = deleteFile(projectId, fileId, "file", time)
        assertNull(streamRecorder.error)
        Mockito.verify(fakeStorage).get(eq(BlobId.of(this.BUCKET_NAME, projectId)))
        val newCoffin = CosmasProto.FileCoffin.newBuilder().
                setProjectId(projectId).
                setFileId(fileId).
                setFileName("file").
                setRemovalTimestamp(time).
                build()
        Mockito.verify(fakeStorage).create(eq(BlobInfo.newBuilder(this.BUCKET_NAME, projectId).build()),
                eq(cemetery.toBuilder().addCemetery(newCoffin).build().toByteArray()))
    }

    private fun getFileFromService(version: Long, fileId: String = "0", projectId: String = "0"): String {
        val (getVersionRecorder, getVersionRequest) = getStreamRecorderAndRequestForGettingVersion(version, fileId, projectId)
        this.service.getVersion(getVersionRequest, getVersionRecorder)
        return getVersionRecorder.values[0].file.content.toStringUtf8()
    }

    private fun getStreamRecorderAndRequestForGettingVersion(version: Long, fileId: String = "0", projectId: String = "0"):
            Pair<StreamRecorder<GetVersionResponse>, GetVersionRequest> {
        val getVersionRecorder: StreamRecorder<GetVersionResponse> = StreamRecorder.create()
        val getVersionRequest = GetVersionRequest
                .newBuilder()
                .setVersion(version)
                .setFileId(fileId)
                .setProjectId(projectId)
                .build()
        return Pair(getVersionRecorder, getVersionRequest)
    }

    private fun createVersion(text: String, fileId: String = "0", projectId: String = "0") {
        val createVersionRecorder: StreamRecorder<CreateVersionResponse> = StreamRecorder.create()
        val newVersionRequest = CreateVersionRequest
                .newBuilder()
                .setFileId(fileId)
                .setProjectId(projectId)
                .setFile(ByteString.copyFromUtf8(text))
                .build()
        this.service.createVersion(newVersionRequest, createVersionRecorder)
    }

    private fun getServiceForTests(): CosmasGoogleCloudService {
        return CosmasGoogleCloudService(this.BUCKET_NAME, LocalStorageHelper.getOptions().service)
    }

    private fun getStreamRecorderAndRequestForVersionList(fileId: String = "0", projectId: String = "0"):
            Pair<StreamRecorder<FileVersionListResponse>, FileVersionListRequest> {
        val listVersionsRecorder: StreamRecorder<FileVersionListResponse> = StreamRecorder.create()
        val listVersionsRequest = FileVersionListRequest
                .newBuilder()
                .setFileId(fileId)
                .setProjectId(projectId)
                .build()
        return Pair(listVersionsRecorder, listVersionsRequest)
    }

    private fun getVersionsList(fileId: String = "0", projectId: String = "0"): List<Long> {
        val (listVersionsRecorder, listVersionsRequest) = getStreamRecorderAndRequestForVersionList(fileId, projectId)
        this.service.fileVersionList(listVersionsRequest, listVersionsRecorder)
        return listVersionsRecorder.values[0].versionsList.map { e -> e.generation }
    }


    private fun getMockedBlob(fileContent: String, generation: Long = 0): Blob {
        val blob = mock(Blob::class.java)
        Mockito.`when`(blob.getContent()).thenReturn(
                FileVersion.newBuilder().setContent(
                        ByteString.copyFrom(fileContent.toByteArray())).build().toByteArray())
        Mockito.`when`(blob.generation).thenReturn(generation)
        return blob
    }

    private fun getMockedBlobWithPatch(fileContent: String, createTime: Long = 0, patchList: MutableList<CosmasProto.Patch>): Blob {
        val blob = mock(Blob::class.java)
        Mockito.`when`(blob.getContent()).thenReturn(FileVersion.newBuilder().addAllPatches(patchList)
                .setContent(ByteString.copyFrom(fileContent.toByteArray())).build().toByteArray())
        Mockito.`when`(blob.createTime).thenReturn(createTime)
        return blob
    }

    private fun getStreamRecorderAndRequestForCommitVersions(projectId: String = "0"):
            Pair<StreamRecorder<CommitVersionResponse>, CommitVersionRequest> {
        val commitRecorder: StreamRecorder<CommitVersionResponse> = StreamRecorder.create()
        val commitRequest = CommitVersionRequest
                .newBuilder()
                .setProjectId(projectId)
                .build()
        return Pair(commitRecorder, commitRequest)
    }

    private fun commit(projectId: String = "0") {
        val (commitRecorder, commitRequest) = getStreamRecorderAndRequestForCommitVersions(projectId)
        this.service.commitVersion(commitRequest, commitRecorder)
    }

    private fun addPatchToService(text: String, userId: String, fileId: String, timeStamp: Long, projectId: String) {
        val createPatchRecorder: StreamRecorder<CosmasProto.CreatePatchResponse> = StreamRecorder.create()
        val newPatch = newPatch(userId, text, timeStamp)
        val newPatchRequest = CosmasProto.CreatePatchRequest.newBuilder()
                .setFileId(fileId)
                .setProjectId(projectId)
                .setPatch(newPatch)
                .build()
        this.service.createPatch(newPatchRequest, createPatchRecorder)
    }

    private fun deletePatch(fileId: String, projectId: String, generation: Long, patchTimestamp: Long): String {
        val deletePatchRecorder: StreamRecorder<DeletePatchResponse> = StreamRecorder.create()
        val deletePatchRequest = DeletePatchRequest
                .newBuilder()
                .setGeneration(generation)
                .setFileId(fileId)
                .setProjectId(projectId)
                .setPatchTimestamp(patchTimestamp)
                .build()
        this.service.deletePatch(deletePatchRequest, deletePatchRecorder)
        return deletePatchRecorder.values[0].content.toStringUtf8()
    }

    private fun newPatch(userId: String, text: String, timeStamp: Long): CosmasProto.Patch {
        return CosmasProto.Patch.newBuilder().setText(text).setUserId(userId).setTimestamp(timeStamp).build()
    }

    private fun deleteFile(projectId: String, fileId: String, fileName: String, time: Long) : StreamRecorder<DeleteFileResponse> {
        val deleteFileRecorder: StreamRecorder<DeleteFileResponse> = StreamRecorder.create()
        val deleteFileRequest = DeleteFileRequest.newBuilder().
                setProjectId(projectId).
                setFileId(fileId).
                setFileName(fileName).
                setRemovalTimestamp(time).
                build()
        this.service.deleteFile(deleteFileRequest, deleteFileRecorder)
        return deleteFileRecorder
    }

    private fun getMockedBlobWithCemetery(cemetery: FileCemetery) : Blob {
        val blob = mock(Blob::class.java)
        Mockito.`when`(blob.getContent()).thenReturn(cemetery.toByteArray())
        return blob
    }
}