// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.hybrid;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.aead.XChaCha20Poly1305Parameters;
import com.google.crypto.tink.internal.BigIntegerEncoding;
import com.google.crypto.tink.subtle.EllipticCurves;
import com.google.crypto.tink.subtle.X25519;
import com.google.crypto.tink.util.Bytes;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public final class EciesPublicKeyTest {
  private static final class NistCurveMapping {
    final EciesParameters.CurveType curveType;
    final EllipticCurves.CurveType ecNistCurve;

    NistCurveMapping(EciesParameters.CurveType curveType, EllipticCurves.CurveType ecNistCurve) {
      this.curveType = curveType;
      this.ecNistCurve = ecNistCurve;
    }
  }

  @DataPoints("nistCurvesMapping")
  public static final NistCurveMapping[] NIST_CURVES =
      new NistCurveMapping[] {
        new NistCurveMapping(
            EciesParameters.CurveType.NIST_P256, EllipticCurves.CurveType.NIST_P256),
        new NistCurveMapping(
            EciesParameters.CurveType.NIST_P384, EllipticCurves.CurveType.NIST_P384),
        new NistCurveMapping(
            EciesParameters.CurveType.NIST_P521, EllipticCurves.CurveType.NIST_P521)
      };

  private static final class PointFormatMapping {
    final EciesParameters.PointFormat pointFormat;
    final EllipticCurves.PointFormatType ecPointFormatType;

    PointFormatMapping(
        EciesParameters.PointFormat pointFormat, EllipticCurves.PointFormatType ecPointFormatType) {
      this.pointFormat = pointFormat;
      this.ecPointFormatType = ecPointFormatType;
    }
  }

  @DataPoints("pointFormatsMapping")
  public static final PointFormatMapping[] POINT_FORMATS =
      new PointFormatMapping[] {
        new PointFormatMapping(
            EciesParameters.PointFormat.UNCOMPRESSED, EllipticCurves.PointFormatType.UNCOMPRESSED),
        new PointFormatMapping(
            EciesParameters.PointFormat.COMPRESSED, EllipticCurves.PointFormatType.COMPRESSED),
        new PointFormatMapping(
            EciesParameters.PointFormat.LEGACY_UNCOMPRESSED,
            EllipticCurves.PointFormatType.DO_NOT_USE_CRUNCHY_UNCOMPRESSED)
      };

  @Test
  public void convertToAndFromJavaECPublicKey() throws Exception {
    // Create an elliptic curve key pair using Java's KeyPairGenerator and get the public key.
    KeyPair keyPair = EllipticCurves.generateKeyPair(EllipticCurves.CurveType.NIST_P256);
    ECPublicKey ecPublicKey = (ECPublicKey) keyPair.getPublic();

    // Before conversion, check that the spec of the ecPublicKey are what we expect.
    assertThat(ecPublicKey.getParams().getCurve())
        .isEqualTo(EllipticCurves.getCurveSpec(EllipticCurves.CurveType.NIST_P256).getCurve());

    // Create EciesParameters that match the curve type.
    EciesParameters parameters =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.NIST_P256)
            .setPointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();

    // Encode the ecPublicKey to bytes using the Elliptic-Curve-Point-to-Octet-String conversion.
    Bytes publicPointBytes =
        Bytes.copyFrom(
            EllipticCurves.pointEncode(
                EllipticCurves.CurveType.NIST_P256,
                EllipticCurves.PointFormatType.UNCOMPRESSED,
                ecPublicKey.getW()));

    // Create EciesPublicKey using the bytes from the ecPublicKey.
    EciesPublicKey publicKey =
        EciesPublicKey.create(parameters, publicPointBytes, /* idRequirement= */ null);

    // Convert EciesPublicKey back into a ECPublicKey.
    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    ECPublicKey ecPublicKey2 =
        (ECPublicKey)
            keyFactory.generatePublic(
                new ECPublicKeySpec(
                    EllipticCurves.pointDecode(
                        EllipticCurves.CurveType.NIST_P256,
                        EllipticCurves.PointFormatType.UNCOMPRESSED,
                        publicKey.getPublicPointBytes().toByteArray()),
                    EllipticCurves.getCurveSpec(EllipticCurves.CurveType.NIST_P256)));
    assertThat(ecPublicKey2.getW()).isEqualTo(ecPublicKey.getW());
    assertThat(ecPublicKey2.getParams().getCurve()).isEqualTo(ecPublicKey.getParams().getCurve());
  }

  @Theory
  public void createNistCurvePublicKey_hasCorrectParameters(
      @FromDataPoints("nistCurvesMapping") NistCurveMapping nistCurveMapping,
      @FromDataPoints("pointFormatsMapping") PointFormatMapping pointFormatMapping)
      throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(nistCurveMapping.curveType)
            .setPointFormat(pointFormatMapping.pointFormat)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    ECPublicKey ecPublicKey =
        (ECPublicKey) EllipticCurves.generateKeyPair(nistCurveMapping.ecNistCurve).getPublic();
    Bytes publicPointBytes =
        Bytes.copyFrom(
            EllipticCurves.pointEncode(
                nistCurveMapping.ecNistCurve,
                pointFormatMapping.ecPointFormatType,
                ecPublicKey.getW()));

    EciesPublicKey publicKey =
        EciesPublicKey.create(params, publicPointBytes, /* idRequirement= */ null);

    assertThat(publicKey.getPublicPointBytes()).isEqualTo(publicPointBytes);
    assertThat(publicKey.getOutputPrefix()).isEqualTo(Bytes.copyFrom(new byte[] {}));
    assertThat(publicKey.getParameters()).isEqualTo(params);
    assertThat(publicKey.getIdRequirementOrNull()).isEqualTo(null);
  }

  @Test
  public void createX25519PublicKey_hasCorrectParameters() throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    Bytes publicPointBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));

    EciesPublicKey publicKey =
        EciesPublicKey.create(params, publicPointBytes, /* idRequirement= */ null);

    assertThat(publicKey.getPublicPointBytes()).isEqualTo(publicPointBytes);
    assertThat(publicKey.getOutputPrefix()).isEqualTo(Bytes.copyFrom(new byte[] {}));
    assertThat(publicKey.getParameters()).isEqualTo(params);
    assertThat(publicKey.getIdRequirementOrNull()).isEqualTo(null);
  }

  @Theory
  public void createNistCurvePublicKey_withWrongKeyLength_fails(
      @FromDataPoints("nistCurvesMapping") NistCurveMapping nistCurveMapping,
      @FromDataPoints("pointFormatsMapping") PointFormatMapping pointFormatMapping)
      throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(nistCurveMapping.curveType)
            .setPointFormat(pointFormatMapping.pointFormat)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    ECPublicKey ecPublicKey =
        (ECPublicKey) EllipticCurves.generateKeyPair(nistCurveMapping.ecNistCurve).getPublic();
    Bytes publicKeyBytes =
        Bytes.copyFrom(
            EllipticCurves.pointEncode(
                nistCurveMapping.ecNistCurve,
                pointFormatMapping.ecPointFormatType,
                ecPublicKey.getW()));
    Bytes tooShort = Bytes.copyFrom(publicKeyBytes.toByteArray(), 0, publicKeyBytes.size() - 1);
    byte[] tooLongBytes = new byte[publicKeyBytes.size() + 1];
    System.arraycopy(publicKeyBytes.toByteArray(), 0, tooLongBytes, 0, publicKeyBytes.size());
    Bytes tooLong = Bytes.copyFrom(tooLongBytes);

    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(params, tooShort, /* idRequirement= */ null));

    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(params, tooLong, /* idRequirement= */ null));
  }

  @Test
  public void createX25519PublicKey_withWrongKeyLength_fails() throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    Bytes publicKeyBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));
    Bytes tooShort = Bytes.copyFrom(publicKeyBytes.toByteArray(), 0, publicKeyBytes.size() - 1);
    byte[] tooLongBytes = new byte[publicKeyBytes.size() + 1];
    System.arraycopy(publicKeyBytes.toByteArray(), 0, tooLongBytes, 0, publicKeyBytes.size());
    Bytes tooLong = Bytes.copyFrom(tooLongBytes);

    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(params, tooShort, /* idRequirement= */ null));

    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(params, tooLong, /* idRequirement= */ null));
  }

  /** Copied from {@link EllipticCurves#pointEncode} to bypass point validation. */
  private static byte[] encodeUncompressedPoint(EllipticCurve curve, ECPoint point)
      throws GeneralSecurityException {
    int coordinateSize = EllipticCurves.fieldSizeInBytes(curve);
    byte[] encoded = new byte[2 * coordinateSize + 1];
    byte[] x = BigIntegerEncoding.toBigEndianBytes(point.getAffineX());
    byte[] y = BigIntegerEncoding.toBigEndianBytes(point.getAffineY());
    // Order of System.arraycopy is important because x,y can have leading 0's.
    System.arraycopy(y, 0, encoded, 1 + 2 * coordinateSize - y.length, y.length);
    System.arraycopy(x, 0, encoded, 1 + coordinateSize - x.length, x.length);
    encoded[0] = 4;
    return encoded;
  }

  @Theory
  public void createNistCurvePublicKey_withPointNotOnCurve_fails(
      @FromDataPoints("nistCurvesMapping") NistCurveMapping nistCurveMapping) throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(nistCurveMapping.curveType)
            .setPointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    ECPublicKey ecPublicKey =
        (ECPublicKey) EllipticCurves.generateKeyPair(nistCurveMapping.ecNistCurve).getPublic();
    ECPoint point = ecPublicKey.getW();
    ECPoint badPoint = new ECPoint(point.getAffineX(), point.getAffineY().subtract(BigInteger.ONE));

    Bytes publicPointBytes =
        Bytes.copyFrom(
            encodeUncompressedPoint(
                EllipticCurves.getCurveSpec(nistCurveMapping.ecNistCurve).getCurve(), badPoint));

    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(params, publicPointBytes, /* idRequirement= */ null));
  }

  @Test
  public void createPublicKeyWithMismatchedIdRequirement_fails() throws Exception {
    EciesParameters.Builder paramsBuilder =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setDemParameters(XChaCha20Poly1305Parameters.create());
    Bytes publicKeyBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));

    EciesParameters noPrefixParams =
        paramsBuilder.setVariant(EciesParameters.Variant.NO_PREFIX).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(noPrefixParams, publicKeyBytes, /* idRequirement= */ 123));

    EciesParameters tinkParams = paramsBuilder.setVariant(EciesParameters.Variant.TINK).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(tinkParams, publicKeyBytes, /* idRequirement= */ null));

    EciesParameters crunchyParams =
        paramsBuilder.setVariant(EciesParameters.Variant.CRUNCHY).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> EciesPublicKey.create(crunchyParams, publicKeyBytes, /* idRequirement= */ null));
  }

  @Test
  public void getOutputPrefix_isCorrect() throws Exception {
    EciesParameters.Builder paramsBuilder =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setDemParameters(XChaCha20Poly1305Parameters.create());
    Bytes publicPointBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));

    EciesParameters noPrefixParams =
        paramsBuilder.setVariant(EciesParameters.Variant.NO_PREFIX).build();
    EciesPublicKey noPrefixPublicKey =
        EciesPublicKey.create(noPrefixParams, publicPointBytes, /* idRequirement= */ null);
    assertThat(noPrefixPublicKey.getIdRequirementOrNull()).isEqualTo(null);
    assertThat(noPrefixPublicKey.getOutputPrefix()).isEqualTo(Bytes.copyFrom(new byte[] {}));

    EciesParameters tinkParams = paramsBuilder.setVariant(EciesParameters.Variant.TINK).build();
    EciesPublicKey tinkPublicKey =
        EciesPublicKey.create(tinkParams, publicPointBytes, /* idRequirement= */ 0x02030405);
    assertThat(tinkPublicKey.getIdRequirementOrNull()).isEqualTo(0x02030405);
    assertThat(tinkPublicKey.getOutputPrefix())
        .isEqualTo(Bytes.copyFrom(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05}));

    EciesParameters crunchyParams =
        paramsBuilder.setVariant(EciesParameters.Variant.CRUNCHY).build();
    EciesPublicKey crunchyPublicKey =
        EciesPublicKey.create(crunchyParams, publicPointBytes, /* idRequirement= */ 0x01020304);
    assertThat(crunchyPublicKey.getIdRequirementOrNull()).isEqualTo(0x01020304);
    assertThat(crunchyPublicKey.getOutputPrefix())
        .isEqualTo(Bytes.copyFrom(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04}));
  }

  @Test
  public void sameKeys_areEqual() throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    Bytes publicPointBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));

    EciesPublicKey publicKey1 =
        EciesPublicKey.create(params, publicPointBytes, /* idRequirement= */ null);
    EciesPublicKey publicKey2 =
        EciesPublicKey.create(params, publicPointBytes, /* idRequirement= */ null);

    assertThat(publicKey1.equalsKey(publicKey2)).isTrue();
  }

  @Test
  public void keysWithDifferentParams_areNotEqual() throws Exception {
    EciesParameters.Builder paramsBuilder =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setDemParameters(XChaCha20Poly1305Parameters.create());
    Bytes publicKeyBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));

    EciesParameters params1 = paramsBuilder.setVariant(EciesParameters.Variant.TINK).build();
    EciesPublicKey publicKey1 =
        EciesPublicKey.create(params1, publicKeyBytes, /* idRequirement= */ 123);
    EciesParameters params2 = paramsBuilder.setVariant(EciesParameters.Variant.CRUNCHY).build();
    EciesPublicKey publicKey2 =
        EciesPublicKey.create(params2, publicKeyBytes, /* idRequirement= */ 123);

    assertThat(publicKey1.equalsKey(publicKey2)).isFalse();
  }

  @Test
  public void keysWithDifferentKeyBytes_areNotEqual() throws Exception {
    EciesParameters params =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .setDemParameters(XChaCha20Poly1305Parameters.create())
            .build();
    Bytes publicKeyBytes1 = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));
    byte[] buf2 = publicKeyBytes1.toByteArray();
    buf2[0] = (byte) (buf2[0] ^ 0x01);
    Bytes publicKeyBytes2 = Bytes.copyFrom(buf2);

    EciesPublicKey publicKey1 =
        EciesPublicKey.create(params, publicKeyBytes1, /* idRequirement= */ null);
    EciesPublicKey publicKey2 =
        EciesPublicKey.create(params, publicKeyBytes2, /* idRequirement= */ null);

    assertThat(publicKey1.equalsKey(publicKey2)).isFalse();
  }

  @Test
  public void keysWithdifferentIds_areNotEqual() throws Exception {
    EciesParameters.Builder paramsBuilder =
        EciesParameters.builder()
            .setHashType(EciesParameters.HashType.SHA256)
            .setCurveType(EciesParameters.CurveType.X25519)
            .setPointFormat(EciesParameters.PointFormat.COMPRESSED)
            .setDemParameters(XChaCha20Poly1305Parameters.create());
    Bytes publicKeyBytes = Bytes.copyFrom(X25519.publicFromPrivate(X25519.generatePrivateKey()));

    EciesParameters params1 = paramsBuilder.setVariant(EciesParameters.Variant.TINK).build();
    EciesPublicKey publicKey1 =
        EciesPublicKey.create(params1, publicKeyBytes, /* idRequirement= */ 123);
    EciesParameters params2 = paramsBuilder.setVariant(EciesParameters.Variant.TINK).build();
    EciesPublicKey publicKey2 =
        EciesPublicKey.create(params2, publicKeyBytes, /* idRequirement= */ 456);

    assertThat(publicKey1.equalsKey(publicKey2)).isFalse();
  }
}
