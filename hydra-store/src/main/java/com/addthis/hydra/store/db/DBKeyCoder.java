/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.store.db;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.addthis.codec.Codec;
import com.addthis.codec.binary.CodecBin2;
import com.addthis.codec.codables.BytesCodable;
import com.addthis.hydra.store.kv.PageEncodeType;
import com.addthis.hydra.store.kv.KeyCoder;
import com.addthis.hydra.store.util.Raw;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;

/**
 */
class DBKeyCoder<V extends BytesCodable> implements KeyCoder<DBKey, V> {

    protected final Codec codec;
    protected static final CodecBin2 codecBin2 = CodecBin2.INSTANCE;
    protected final Class<? extends V> clazz;

    private static final byte[] zero = new byte[0];
    private static final byte[] negInfBytes = new DBKey(0, (Raw)null).toBytes();

    public DBKeyCoder(Class<? extends V> clazz) {
        this(codecBin2, clazz);
    }

    public DBKeyCoder(Codec codec, Class<? extends V> clazz) {
        this.codec = codec;
        this.clazz = clazz;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBKey negInfinity() {
        return new DBKey(0, (Raw) null);
    }

    @Override
    public byte[] encodedNegInfinity() {
        return negInfBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] keyEncode(DBKey key) {
        if (key == null) {
            return zero;
        } else {
            return key.toBytes();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] keyEncode(@Nullable DBKey key, @Nonnull DBKey baseKey, @Nonnull PageEncodeType encodeType) {
        if (key == null) {
            return zero;
        }
        switch (encodeType) {
            case LEGACY:
            case SPARSE:
                return key.toBytes();
            case LONGIDS:
                return key.deltaEncode(baseKey);
            default:
                throw new RuntimeException("Unknown encoding type: " + encodeType);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] valueEncode(V value, @Nonnull PageEncodeType encodeType) {
        try {
            switch (encodeType) {
                case LEGACY:
                    return codec.encode(value);
                case SPARSE:
                case LONGIDS:
                    if (value == null) {
                        return zero;
                    } else {
                        return value.bytesEncode(encodeType.ordinal());
                    }
                default:
                    throw new RuntimeException("Unknown encoding type: " + encodeType);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBKey keyDecode(byte[] key) {
        if (key == null || key.length == 0) {
            return null;
        } else {
            return DBKey.fromBytes(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DBKey keyDecode(@Nullable byte[] key, @Nonnull DBKey baseKey, @Nonnull PageEncodeType encodeType) {
        if (key == null || key.length == 0) {
            return null;
        } else {
            switch (encodeType) {
                case LEGACY:
                case SPARSE:
                    return DBKey.fromBytes(key);
                case LONGIDS:
                    return DBKey.deltaDecode(key, baseKey);
                default:
                    throw new RuntimeException("Unknown encoding type: " + encodeType);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V valueDecode(byte[] value, @Nonnull PageEncodeType encodeType) {

        try {
            switch (encodeType) {
                case LEGACY:
                    return codec.decode(clazz.newInstance(), value);
                case SPARSE:
                case LONGIDS:
                    if (value.length > 0) {
                        V v = clazz.newInstance();
                        v.bytesDecode(value, encodeType.ordinal());
                        return v;
                    } else {
                        return null;
                    }
                default:
                    throw new RuntimeException("Unknown encoding type: " + encodeType);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("codec", codec)
                .add("clazz", clazz)
                .toString();
    }
}
