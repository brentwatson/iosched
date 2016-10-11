/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
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
 */

package com.google.samples.apps.iosched.util

import com.google.common.base.Charsets
import java.io.*

/**
 * Utility methods and constants used for writing and reading to from streams and files.
 */
object IOUtils {

    /**
     * Writes the given string to a [File].

     * @param data The data to be written to the File.
     * *
     * @param file The File to write to.
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeToFile(data: String, file: File) {
        writeToFile(data.toByteArray(Charsets.UTF_8), file)
    }

    /**
     * Write the given bytes to a [File].

     * @param data The bytes to be written to the File.
     * *
     * @param file The [File] to be used for writing the data.
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeToFile(data: ByteArray, file: File) {
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(file)
            os.write(data)
            os.flush()
            // Perform an fsync on the FileOutputStream.
            os.fd.sync()
        } finally {
            if (os != null) {
                os.close()
            }
        }
    }

    /**
     * Write the given content to an [OutputStream]
     *
     *
     * Note: This method closes the given OutputStream.

     * @param content The String content to write to the OutputStream.
     * *
     * @param os The OutputStream to which the content should be written.
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeToStream(content: String, os: OutputStream) {
        var writer: BufferedWriter? = null
        try {
            writer = BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8))
            writer.write(content)
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }

    /**
     * Reads a [File] as a String

     * @param file The file to be read in.
     * *
     * @return Returns the contents of the File as a String.
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readFileAsString(file: File): String {
        return readAsString(FileInputStream(file))
    }

    /**
     * Reads an [InputStream] into a String using the UTF-8 encoding.
     * Note that this method closes the InputStream passed to it.
     * @param is The InputStream to be read.
     * *
     * @return The contents of the InputStream as a String.
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readAsString(`is`: InputStream): String {
        var reader: BufferedReader? = null
        val sb = StringBuilder()
        try {
            var line: String? = "temp"
            reader = BufferedReader(InputStreamReader(`is`, Charsets.UTF_8))
            while (line != null) {
                line = reader.readLine()
                sb.append(line)
            }
        } finally {
            if (reader != null) {
                reader.close()
            }
        }
        return sb.toString()
    }
}
