package au.org.ala.spatial.analysis.layers;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public class OccurrenceDensityLayerGenerator extends CalculatedLayerGenerator {

    public OccurrenceDensityLayerGenerator(BigDecimal resolution, File cellOccurrenceCountsFile) throws IOException {
        super(resolution);
        readCellOccurrenceCounts(cellOccurrenceCountsFile);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("args[0]=Resolution in degrees, e.g. 0.1 for 0.1 by 0.1 degree cells\n"
                    + "args[1]=Path to cell occurrence counts file (should be generated by biocache store via jenkins - file resolution must match the resolution provided to this tool)\n"
                    + "args[2]=Path of directory in which to write output files\n"
                    + "args[3]=Prefix to use for names of output files.\n");

            return;
        }

        BigDecimal resolution = new BigDecimal(args[0]).setScale(2);
        File cellOccurrenceCountsFile = new File(args[1]);
        File outputFileDirectory = new File(args[2]);
        String outputFileNamePrefix = args[3];

        new OccurrenceDensityLayerGenerator(resolution, cellOccurrenceCountsFile).writeGrid(outputFileDirectory, outputFileNamePrefix);
    }

    @Override
    protected float handleCell(Map.Entry<BigDecimal, BigDecimal> coordPair, float maxValue, PrintWriter ascPrintWriter, BufferedOutputStream divaOutputStream) throws IOException {
        if (_cellOccurrenceCounts.containsKey(coordPair)) {
            // Write species richness value for the cell. This is the number of
            // occurrence records in the cell.

            long cellOccurrenceCount = _cellOccurrenceCounts.get(coordPair);

            float newMaxValue = 0;
            if (maxValue < cellOccurrenceCount) {
                newMaxValue = cellOccurrenceCount;
            } else {
                newMaxValue = maxValue;
            }

            ascPrintWriter.print(cellOccurrenceCount);

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE]);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.putFloat(cellOccurrenceCount);
            divaOutputStream.write(bb.array());

            return newMaxValue;
        } else {
            // No species occurrences in this cell. Occurrence density value
            // is zero.
            ascPrintWriter.print("0");

            ByteBuffer bb = ByteBuffer.wrap(new byte[Float.SIZE / Byte.SIZE]);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.putFloat(0);
            divaOutputStream.write(bb.array());
            return maxValue;
        }
    }

}
