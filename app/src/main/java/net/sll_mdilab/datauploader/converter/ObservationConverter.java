package net.sll_mdilab.datauploader.converter;

import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;

import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.primitive.DateTimeDt;

public class ObservationConverter {

    public static final String DEFAULT_PATIENT_ID = "5212121212";
    public static final String PULSE_RATE_CODE = "MDC_PULS_OXIM_PULS_RATE";
    public static final String PULSE_RATE_UNIT = "MDC_DIM_BEAT_PER_MIN";
    public static final String CODE_SYSTEM = "MDC";

    private float getValue(DataPoint dataPoint) {
        return dataPoint.getValue(getField(dataPoint.getDataType())).asFloat();
    }

    private String getObservationCode(DataPoint dataPoint) {
        if (DataType.TYPE_HEART_RATE_BPM.equals(dataPoint.getDataType())) {
            return PULSE_RATE_CODE;
        } else {
            throw new RuntimeException("Unknown data type " + dataPoint.getDataType().getName());
        }
    }

    private String getObservationUnit(DataPoint dataPoint) {
        if (DataType.TYPE_HEART_RATE_BPM.equals(dataPoint.getDataType())) {
            return PULSE_RATE_UNIT;
        } else {
            throw new RuntimeException("Unknown data type " + dataPoint.getDataType().getName());
        }
    }

    private Field getField(DataType dataType) {
        if (DataType.TYPE_HEART_RATE_BPM.equals(dataType)) {
            return Field.FIELD_BPM;
        } else {
            throw new RuntimeException("Unknown data type " + dataType.getName());
        }
    }

    public Observation convertToObservation(DataPoint dataPoint, String patientId) {
        if(patientId == null) {
            patientId = DEFAULT_PATIENT_ID;
        }

        return createObservation(patientId, getObservationCode
                (dataPoint), getValue(dataPoint), getObservationUnit(dataPoint), new
                Date(dataPoint.getTimestamp(TimeUnit.MILLISECONDS)));
    }

    private Observation createObservation(String patientId, String code, double value, String
            unit, Date dateTime) {
        Observation obs = new Observation();

        obs.setSubject(new ResourceReferenceDt("Patient/" + patientId));
        obs.setCode(new CodeableConceptDt().addCoding(new CodingDt().setCode(code).setSystem(CODE_SYSTEM)));
        QuantityDt quantity = new QuantityDt()
                .setValue(value)
                .setUnit(unit);

        obs.setValue(quantity);
        obs.setPerformer(Arrays.asList(new ResourceReferenceDt("Patient/" + patientId)));

        obs.setEffective(convertTime(dateTime));

        return obs;
    }

    private DateTimeDt convertTime(Date date) {
        DateTimeDt obsTimeStamp = new DateTimeDt(date);
        obsTimeStamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        return obsTimeStamp;
    }
}
