package com.regnosys.testing.schemeimport;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2024 REGnosys
 * ===============
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ===============
 */

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LatestSchemesImportTest {

    private static final String SCHEMA_PATH = "src/main/resources/coding-schemes/fpml";
    private static final String CODE_LIST_ZIP = "src/main/resources/coding-schemes/fpml/codelist.zip";
    private static final String CODE_LIST = "src/main/resources/coding-schemes/fpml/codelist";
    private static final boolean WRITE_LATEST_VERSION = Optional.ofNullable(System.getenv("WRITE_EXPECTATIONS"))
            .map(Boolean::parseBoolean).orElse(false);

    @Test
    public void downloadLatestVersions() throws IOException, NoSuchAlgorithmException {
        URL website = new URL("https://www.fpml.org/spec/coding-scheme/codelist.zip");
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());

        try (FileOutputStream fos = new FileOutputStream(LatestSchemesImportTest.CODE_LIST_ZIP)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            String checksum = getZipCheckSum(Paths.get(CODE_LIST_ZIP));
            if (WRITE_LATEST_VERSION) {
                //Unzip from CodeList just being downloaded
                unzip();
                //Move the unzipped files to SchemePath if not already exists
                moveFilesToFpml();
                deleteFileFolder(new File(LatestSchemesImportTest.CODE_LIST_ZIP));
                deleteFileFolder(new File(LatestSchemesImportTest.CODE_LIST));
            }
            assertEquals("2479f3e4e345730f2440a1f1cb642426", checksum, "CodeList zip has been updated, run again with WRITE_EXPECTATIONS enabled then update expected checksum");
        }
    }

    private String getZipCheckSum(Path zipPath) throws IOException, NoSuchAlgorithmException {
        byte[] data = Files.readAllBytes(zipPath);
        byte[] hash = MessageDigest.getInstance("MD5").digest(data);
        return new BigInteger(1, hash).toString(16);
    }

    private static void unzip() {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(Paths.get(CODE_LIST_ZIP).toFile().toPath()))) {

            // list files in zip
            ZipEntry zipEntry = zipInputStream.getNextEntry();

            while (zipEntry != null) {
                // Check for zip slip vulnerability attack
                Path newUnzipPath = zipSlipVulnerabilityProtect(zipEntry, Paths.get(SCHEMA_PATH));

                boolean isDirectory = false;
                //check for files or directory
                if (zipEntry.getName().endsWith(File.separator)) {
                    isDirectory = true;
                }

                if (isDirectory) {
                    Files.createDirectories(newUnzipPath);
                } else {

                    if (newUnzipPath.getParent() != null) {
                        if (Files.notExists(newUnzipPath.getParent())) {
                            Files.createDirectories(newUnzipPath.getParent());
                        }
                    }

                    // copy files using nio
                    Files.copy(zipInputStream, newUnzipPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Check for zip slip attack
    private static Path zipSlipVulnerabilityProtect(ZipEntry zipEntry, Path targetDir)
            throws IOException {

        /*
          resolve(String other) method of java. nio. file.Path used to converts a given
          path string to a Path and resolves it against this Path in the exact same manner
          as specified by the resolve method
         */
        Path dirResolved = targetDir.resolve(zipEntry.getName());

        /*
          Normalizing a path involves modifying the string that identifies a
          path or file so that it conforms to a valid path on the target operating system.
         */
        //normalize the path on target directory or else throw exception
        Path normalizePath = dirResolved.normalize();
        if (!normalizePath.startsWith(targetDir)) {
            throw new IOException("Invalid zip: " + zipEntry.getName());
        }

        return normalizePath;
    }

    private void moveFilesToFpml() {
        Path sourceDir = Paths.get(CODE_LIST);
        Path destinationDir = Paths.get(SCHEMA_PATH);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(sourceDir)) {
            for (Path path : directoryStream) {
                System.out.println("copying " + path.toString());
                Path d2 = destinationDir.resolve(path.getFileName());
                System.out.println("destination File=" + d2);
                if (Files.notExists(d2))
                    Files.move(path, d2, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void deleteFileFolder(File fileToBeDeleted) {
        File[] contents = fileToBeDeleted.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    deleteFileFolder(f);
                }
            }
        }
        fileToBeDeleted.delete();
    }
}
