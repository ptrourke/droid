/**
 * Copyright (c) 2012, The National Archives <pronom@nationalarchives.gsi.gov.uk>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following
 * conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of the The National Archives nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package uk.gov.nationalarchives.droid.command.archive;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;

import org.jwat.arc.ArcReader;
import org.jwat.arc.ArcReaderFactory;
import org.jwat.arc.ArcRecord;
import org.jwat.arc.ArcRecordBase;

import uk.gov.nationalarchives.droid.command.ResultPrinter;
import uk.gov.nationalarchives.droid.command.action.CommandExecutionException;
import uk.gov.nationalarchives.droid.container.ContainerSignatureDefinitions;
import uk.gov.nationalarchives.droid.core.BinarySignatureIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationRequest;
import uk.gov.nationalarchives.droid.core.interfaces.IdentificationResultCollection;
import uk.gov.nationalarchives.droid.core.interfaces.RequestIdentifier;
import uk.gov.nationalarchives.droid.core.interfaces.archive.WebArchiveEntryRequestFactory;
import uk.gov.nationalarchives.droid.core.interfaces.archive.IdentificationRequestFactory;
import uk.gov.nationalarchives.droid.core.interfaces.resource.RequestMetaData;

/**
 * Identifier for files held in an ARC archive.
 * 
 * @author rbrennan
 */
public class ArcArchiveContentIdentifier {

    private BinarySignatureIdentifier binarySignatureIdentifier;
    private ContainerSignatureDefinitions containerSignatureDefinitions;
    private String path;
    private String slash;
    private String slash1;
    private File tmpDir;
    
    /**
     * 
     * @param binarySignatureIdentifier     binary signature identifier
     * @param containerSignatureDefinitions container signatures
     * @param path                          current archive path 
     * @param slash                         local path element delimiter
     * @param slash1                        local first container prefix delimiter
     */
    public ArcArchiveContentIdentifier(final BinarySignatureIdentifier binarySignatureIdentifier,
            final ContainerSignatureDefinitions containerSignatureDefinitions,
            final String path, final String slash, final String slash1) {
    
        synchronized (this) {
            this.binarySignatureIdentifier = binarySignatureIdentifier;
            this.containerSignatureDefinitions = containerSignatureDefinitions;
            this.path = path;
            this.slash = slash;
            this.slash1 = slash1;
            if (tmpDir == null) {
                tmpDir = new File(System.getProperty("java.io.tmpdir"));
            }
        }
    }
    
    /**
     * @param uri The URI of the file to identify
     * @param request The Identification Request
     * @throws CommandExecutionException When an exception happens during execution
     * @throws CommandExecutionException When an exception happens during archive access
     */
    public void identify(final URI uri, final IdentificationRequest request)
        throws CommandExecutionException {
        /**
         * Save importing all the http codes
         */
        final int httpACCEPTED = 200;
        
        final String newPath = "arc:" + slash1 + path + request.getFileName() + "!" + slash;
        slash1 = "";
        final IdentificationRequestFactory factory  = new WebArchiveEntryRequestFactory();
        try {
            final InputStream arcIn = request.getSourceInputStream();
            ArcReader arcReader = ArcReaderFactory.getReader(arcIn);
            Iterator<ArcRecordBase> iterator = arcReader.iterator();
            try {
                ArcRecordBase base = null;
                while (iterator.hasNext()) {
                    base = iterator.next();
                    // skip the header record at the start and any dns requests
                    if (base instanceof ArcRecord
                        && base.getHttpHeader() != null
                        && base.getHttpHeader().statusCode == httpACCEPTED) {
                        // no directory structure, so we use the full url as name
                        String name = base.getUrl().toString();

                        RequestMetaData metaData = new RequestMetaData(
                                base.getArchiveLength(),
                                base.getArchiveDate().getTime(),
                            name);

                        final RequestIdentifier identifier = new RequestIdentifier(uri);

                        IdentificationRequest arcRequest = factory.newRequest(metaData, identifier);
                        arcRequest.open(base.getPayloadContent());
                        final IdentificationResultCollection arcResults =
                            binarySignatureIdentifier.matchBinarySignatures(arcRequest);
                        final ResultPrinter resultPrinter =
                            new ResultPrinter(binarySignatureIdentifier,
                                containerSignatureDefinitions, newPath, slash, slash1, true, true);
                        resultPrinter.print(arcResults, arcRequest);
                        arcRequest.close();
                    }
                }
            } finally {
                if (arcIn != null) {
                    arcIn.close();
                }
            }
        } catch (IOException ioe) {
            System.err.println(ioe + " (" + newPath + ")"); // continue after corrupt archive 
        }
    }
}
