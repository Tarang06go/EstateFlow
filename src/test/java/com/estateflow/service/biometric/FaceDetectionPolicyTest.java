package com.estateflow.service.biometric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FaceDetectionPolicyTest {
    @Test void zeroDetectionsAreReportedSeparately() {
        FaceProcessingException error=assertThrows(FaceProcessingException.class,
                ()->FaceDetectionPolicy.requireExactlyOne(0));
        assertEquals(FaceProcessingException.Reason.NO_FACE,error.reason());
    }

    @Test void multipleDetectionsAreRejected() {
        FaceProcessingException error=assertThrows(FaceProcessingException.class,
                ()->FaceDetectionPolicy.requireExactlyOne(2));
        assertEquals(FaceProcessingException.Reason.MULTIPLE_FACES,error.reason());
    }

    @Test void oneDetectionPassesAndNegativeCountsAreInvalid() throws Exception {
        assertDoesNotThrow(()->FaceDetectionPolicy.requireExactlyOne(1));
        assertThrows(IllegalArgumentException.class,()->FaceDetectionPolicy.requireExactlyOne(-1));
    }

    @Test void validSFaceFeatureCreatesRedactedEmbeddingAndInvalidFeatureIsRejected() {
        float[] feature=new float[128]; feature[0]=1;
        try(FaceEmbedding embedding=new FaceEmbedding(feature)) {
            assertEquals(1,embedding.similarity(embedding),.00001);
            assertEquals("FaceEmbedding[redacted]",embedding.toString());
        }
        assertThrows(IllegalArgumentException.class,()->new FaceEmbedding(new float[127]));
    }

    @Test void detectionExceptionsRetainDistinctReason() {
        FaceProcessingException error=new FaceProcessingException(FaceProcessingException.Reason.DETECTION,
                "Face detection failed.",new IllegalStateException("synthetic"));
        assertEquals(FaceProcessingException.Reason.DETECTION,error.reason());
        assertNotNull(error.getCause());
    }
}
