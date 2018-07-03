/*
 * Copyright (C) 2018 The Holodeck B2B Team, Sander Fieten
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.as2.util;

import java.util.Base64;
import java.util.List;

import org.holodeckb2b.interfaces.security.ISignatureProcessingResult;
import org.holodeckb2b.interfaces.security.ISignedPartMetadata;

/**
 * Contains the information about how the contant of the AS2 was signed, i.e. the digest algorihtm and digest.
 * Implements {@link ISignedPartMetadata} so it can be used in a {@link ISignatureProcessingResult} to report the 
 * result of signature verification.
 *
 * @author Sander Fieten (sander at chasquis-consulting.com)
 */
public class SignedContentMetadata implements ISignedPartMetadata {
    /**
     * The name of the digest algorithm
     */
    private final String      algorithm;
    /**
     * The digest value, base64 encoded
     */
    private final String      digest;

    /**
     * Creates a new instance with the given algorithm and digest.
     *
     * @param algorithm     The name of the digest algorithm used
     * @param digest        The digest value as bytes
     */
    public SignedContentMetadata(final String algorithm, final byte[] digest) {
        this.algorithm = algorithm;
        this.digest = Base64.getEncoder().encodeToString(digest);
    }

    @Override
    public String getDigestValue() {
        return digest;
    }

    @Override
    public String getDigestAlgorithm() {
        return algorithm;
    }

    /**
     * Not applicable to AS2 signatures as there are no transformations applied.
     *
     * @return
     */
    @Override
    public List<ITransformMetadata> getTransforms() {
        return null;
    }

}

