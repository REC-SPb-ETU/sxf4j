/*
 * Copyright 2017 iserge.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cleanlogic.sxf4j.io;

import com.vividsolutions.jts.geom.*;
import org.cleanlogic.sxf4j.enums.MetricElementSize;
import org.cleanlogic.sxf4j.enums.TextEncoding;
import org.cleanlogic.sxf4j.enums.TextMetricAlign;
import org.cleanlogic.sxf4j.format.SXFPassport;
import org.cleanlogic.sxf4j.format.SXFRecordHeader;
import org.cleanlogic.sxf4j.format.SXFRecordMetric;
import org.cleanlogic.sxf4j.format.SXFRecordMetricText;
import org.cleanlogic.sxf4j.utils.Utils;

import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge Silaev aka iSergio <s.serge.b@gmail.com>
 */
public class SXFRecordMetricReader {
    private final SXFPassport _sxfPassport;
    private final MappedByteBuffer _mappedByteBuffer;
    private final SXFReaderOptions _sxfReaderOptions;
    private final GeometryFactory _srcGeometryFactory;
    private GeometryFactory _dstGeometryFactory;

    public SXFRecordMetricReader(SXFPassport sxfPassport) {
        _sxfPassport = sxfPassport;
        _mappedByteBuffer = sxfPassport.getMappedByteBuffer();
        _sxfReaderOptions = sxfPassport.getReaderOptions();

        int srcSRID = _sxfReaderOptions.srcSRID;
        if (srcSRID == 0) {
            srcSRID = _sxfPassport.epsg;
            if (srcSRID == 0) {
                srcSRID = Utils.detectSRID(_sxfPassport);
                if (srcSRID == 0) {
                    srcSRID = _sxfPassport.getReaderOptions().srcSRID;
                }
            }
        }

        _srcGeometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), srcSRID);
        if (_sxfReaderOptions.proj != null) {
            _dstGeometryFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING_SINGLE), _sxfReaderOptions.dstSRID);
        }
    }

    public SXFRecordMetric read(SXFRecordHeader sxfRecordHeader) {
//        System.err.printf("%d\n", sxfRecordHeader.number);
        _mappedByteBuffer.position((int) sxfRecordHeader.metricOffset);

        List<Coordinate> recordSrcCoordinates = new ArrayList<>();
        List<Coordinate> recordDstCoordinates = new ArrayList<>();

        // Big objects
        int pointCount = (sxfRecordHeader.pointCount == 65535) ? sxfRecordHeader.bigRecordPointCount : sxfRecordHeader.pointCount;

        for (int i = 0; i < pointCount; i++) {
            Coordinate coordinate = readCoordinate(sxfRecordHeader, _sxfReaderOptions.flipCoordinates);
            coordinate = _sxfPassport.fromDescret(coordinate);
            if (_sxfReaderOptions.flipCoordinates) {
                coordinate = new Coordinate(coordinate.y, coordinate.x, coordinate.z);
            }
            recordSrcCoordinates.add(coordinate);
            if (_sxfReaderOptions.proj != null) {
                recordDstCoordinates.add(_sxfReaderOptions.proj.doConvert(coordinate));
            }
//            if (_CTS != null) {
//                try {
//                    recordDstCoordinates.add(_CTS.doConvert(coordinate));
//                } catch (IllegalCoordinateException e) {
//                    e.printStackTrace();
//                }
//            }
        }
        // After main metric may be text metric
        List<SXFRecordMetricText> sxfRecordMetricTexts = new ArrayList<>();
        if (sxfRecordHeader.isText) {
            SXFRecordMetricText sxfRecordMetricText = readText(sxfRecordHeader.isUnicode);
            sxfRecordMetricTexts.add(sxfRecordMetricText);
        }

        List<List<Coordinate>> subrecordsSrcCoordinates = new ArrayList<>();
        List<List<Coordinate>> subrecordsDstCoordinates = new ArrayList<>();
        // Process subject
        for (int i = 0; i < sxfRecordHeader.subjectCount; i++) {
            // First two bytes is reserver, skip them
//            _mappedByteBuffer.position(_mappedByteBuffer.position() + 2);
            _mappedByteBuffer.getShort();
            pointCount = _mappedByteBuffer.getShort();
            List<Coordinate> subrecordSrcCoordinates =  new ArrayList<>();
            List<Coordinate> subrecordDstCoordinates =  new ArrayList<>();
            for (int k = 0; k < pointCount; k++) {
                Coordinate coordinate = readCoordinate(sxfRecordHeader, _sxfReaderOptions.flipCoordinates);
                coordinate = _sxfPassport.fromDescret(coordinate);
                if (_sxfReaderOptions.flipCoordinates) {
                    coordinate = new Coordinate(coordinate.y, coordinate.x, coordinate.z);
                }
                subrecordSrcCoordinates.add(coordinate);
                if (_sxfReaderOptions.proj != null) {
                    subrecordDstCoordinates.add(_sxfReaderOptions.proj.doConvert(coordinate));
                }
//                if (_CTS != null) {
//                    try {
//                        subrecordDstCoordinates.add(_CTS.doConvert(coordinate));
//                    } catch (IllegalCoordinateException e) {
//                        e.printStackTrace();
//                    }
//                }
            }
            subrecordsSrcCoordinates.add(subrecordSrcCoordinates);
            subrecordsDstCoordinates.add(subrecordDstCoordinates);
            // Text of subjects
            if (sxfRecordHeader.isText) {
                SXFRecordMetricText sxfRecordMetricText = readText(sxfRecordHeader.isUnicode);
                sxfRecordMetricTexts.add(sxfRecordMetricText);
            }
        }

        Geometry srcGeometry = null;
        Geometry dstGeometry = null;
        switch (sxfRecordHeader.local) {
            case LINE:
            case TITLE:
            case VECTOR:
            case MIXED:
                // Source geometry in coordinate, projection system of map
                srcGeometry = createMultiLineString(recordSrcCoordinates, subrecordsSrcCoordinates, _srcGeometryFactory);
                if (recordDstCoordinates.size() > 0) {
                    dstGeometry = createMultiLineString(recordDstCoordinates, subrecordsDstCoordinates, _dstGeometryFactory);
                }
                break;
            case POINT:
                srcGeometry = createMultiPoint(recordSrcCoordinates, subrecordsSrcCoordinates, _srcGeometryFactory);
                if (recordDstCoordinates.size() > 0) {
                    dstGeometry = createMultiPoint(recordDstCoordinates, subrecordsDstCoordinates, _dstGeometryFactory);
                }
                break;
            case SQUARE: {
                srcGeometry = createMultiPolygon(recordSrcCoordinates, subrecordsSrcCoordinates, _srcGeometryFactory);
                if (recordDstCoordinates.size() > 0) {
                    dstGeometry = createMultiPolygon(recordDstCoordinates, subrecordsDstCoordinates, _dstGeometryFactory);
                }
            } break;
            default: break;
        }
        SXFRecordMetric sxfRecordMetric = new SXFRecordMetric(srcGeometry, dstGeometry);
        if (sxfRecordHeader.isText) {
            sxfRecordMetric.metricTexts = sxfRecordMetricTexts;
        }

        return sxfRecordMetric;
    }

    /**
     * Read coordinate by MappedByteBuffer. Function not will be used directly!
     * @param sxfRecordHeader record header.
     * @return {@link Coordinate} in descrets.
     */
    private Coordinate readCoordinate(SXFRecordHeader sxfRecordHeader, boolean flipCoordinate) {
        double x = 0.;
        double y = 0.;
        double z = 0.;

        if (!sxfRecordHeader.isFloat) {
            if (sxfRecordHeader.metricElementSize == MetricElementSize.SHORT) {
                x = _mappedByteBuffer.getShort();
                y = _mappedByteBuffer.getShort();
            } else if (sxfRecordHeader.metricElementSize == MetricElementSize.INT) {
                x = _mappedByteBuffer.getInt();
                y = _mappedByteBuffer.getInt();
            }

            if (sxfRecordHeader.is3D) {
                z = _mappedByteBuffer.getFloat();
            }
        } else {
            if (sxfRecordHeader.metricElementSize == MetricElementSize.FLOAT) {
                x = _mappedByteBuffer.getFloat();
                y = _mappedByteBuffer.getFloat();

                if (sxfRecordHeader.is3D) {
                    z = _mappedByteBuffer.getFloat();
                }
            } else if (sxfRecordHeader.metricElementSize == MetricElementSize.DOUBLE) {
                x = _mappedByteBuffer.getDouble();
                y = _mappedByteBuffer.getDouble();

                if (sxfRecordHeader.is3D) {
                    z = _mappedByteBuffer.getDouble();
                }
            }
        }

        return new Coordinate(x, y, z);
    }

    /**
     * Read text of metric and they align. Function not will be used directly!
     * @return {@link SXFRecordMetricText}
     */
    private SXFRecordMetricText readText(boolean isUnicode) {
        int length = _mappedByteBuffer.get() & 0xFF;
        byte[] string = new byte[length];
        _mappedByteBuffer.get(string);
        // Final 0x00 by documentation
        byte zero   = _mappedByteBuffer.get();
        int strlen = 0;
        for (byte b : string) {
            if (b == 0x00) {
                break;
            }
            strlen++;
        }
        String text = "";
        try {
            if (!isUnicode) {
                text = new String(string, TextEncoding.CP1251.getName()).substring(0, strlen).intern();
            } else {
                text = new String(string).substring(0, strlen).intern();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        TextMetricAlign align = TextMetricAlign.BASELINE_LEFT;
        if (strlen + 1 < length) {
            byte c = string[strlen + 1];
            align = TextMetricAlign.fromValue(c);
        } else {
            // If final zero != 0x00, use them
            if (zero != 0x00) {
                align = TextMetricAlign.fromValue(zero);
            }
        }

        return new SXFRecordMetricText(text, align);
    }

    private Geometry createMultiLineString(List<Coordinate> recordCoordinates, List<List<Coordinate>> subrecordsCoordinates, GeometryFactory geometryFactory) {
        if (recordCoordinates.size() == 1) {
            recordCoordinates.add(recordCoordinates.get(0));
        }
        LineString[] lineStrings = new LineString[1 + subrecordsCoordinates.size()];
        lineStrings[0] = _srcGeometryFactory.createLineString(recordCoordinates.toArray(new Coordinate[recordCoordinates.size()]));
        for (int i = 0; i < subrecordsCoordinates.size(); i++) {
            List<Coordinate> coordinates = subrecordsCoordinates.get(i);
            if (coordinates.size() == 1) {
                coordinates.add(coordinates.get(0));
            }
            lineStrings[i + 1] = _srcGeometryFactory.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
        }

        return geometryFactory.createMultiLineString(lineStrings);
    }

    private Geometry createMultiPoint(List<Coordinate> recordCoordinates, List<List<Coordinate>> subrecordsCoordinates, GeometryFactory geometryFactory) {
        Point[] points = new Point[1 + subrecordsCoordinates.size()];
        points[0] = _srcGeometryFactory.createPoint(recordCoordinates.get(0));
        for (int i = 0; i < subrecordsCoordinates.size(); i++) {
            List<Coordinate> coordinates = subrecordsCoordinates.get(i);
            points[i + 1] = _srcGeometryFactory.createPoint(coordinates.get(0));
        }

        return geometryFactory.createMultiPoint(points);
    }

    private Geometry createMultiPolygon(List<Coordinate> recordCoordinates, List<List<Coordinate>> subrecordsCoordinates, GeometryFactory geometryFactory) {
        LinearRing shell = geometryFactory.createLinearRing(recordCoordinates.toArray(new Coordinate[recordCoordinates.size()]));
        LinearRing[] holes = new LinearRing[subrecordsCoordinates.size()];
        for (int i = 0; i < subrecordsCoordinates.size(); i++) {
            List<Coordinate> coordinates = subrecordsCoordinates.get(i);
            holes[i] = geometryFactory.createLinearRing(coordinates.toArray(new Coordinate[coordinates.size()]));
        }
        Polygon polygon = geometryFactory.createPolygon(shell, holes);

        return  geometryFactory.createMultiPolygon(new Polygon[]{polygon});
    }
}
