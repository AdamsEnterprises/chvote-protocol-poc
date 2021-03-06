/*-------------------------------------------------------------------------------------------------
 - #%L                                                                                            -
 - chvote-protocol-poc                                                                            -
 - %%                                                                                             -
 - Copyright (C) 2016 - 2017 République et Canton de Genève                                       -
 - %%                                                                                             -
 - This program is free software: you can redistribute it and/or modify                           -
 - it under the terms of the GNU Affero General Public License as published by                    -
 - the Free Software Foundation, either version 3 of the License, or                              -
 - (at your option) any later version.                                                            -
 -                                                                                                -
 - This program is distributed in the hope that it will be useful,                                -
 - but WITHOUT ANY WARRANTY; without even the implied warranty of                                 -
 - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the                                   -
 - GNU General Public License for more details.                                                   -
 -                                                                                                -
 - You should have received a copy of the GNU Affero General Public License                       -
 - along with this program. If not, see <http://www.gnu.org/licenses/>.                           -
 - #L%                                                                                            -
 -------------------------------------------------------------------------------------------------*/

package ch.ge.ve.protopoc.service.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Model class used to represent a "re-encryption", i.e. the resulting encryption and the randomness used
 */
public final class ReEncryption {
    private final Encryption encryption;
    private final BigInteger randomness;

    public ReEncryption(Encryption encryption, BigInteger randomness) {
        this.encryption = encryption;
        this.randomness = randomness;
    }

    public Encryption getEncryption() {
        return encryption;
    }

    public BigInteger getRandomness() {
        return randomness;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReEncryption that = (ReEncryption) o;
        return Objects.equals(encryption, that.encryption) &&
                Objects.equals(randomness, that.randomness);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encryption, randomness);
    }

    @Override
    public String toString() {
        return "ReEncryption{" +
                "encryption=" + encryption +
                ", randomness=" + randomness +
                '}';
    }
}
