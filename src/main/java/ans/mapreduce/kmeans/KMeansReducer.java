package ans.mapreduce.kmeans;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


// calculate a new clustercenter for these vertices
public class KMeansReducer extends
        Reducer<ClusterCenter, Vector, ClusterCenter, Vector> {

    public static enum Counter {
        CONVERGED
    }

    List<ClusterCenter> centers = new LinkedList<ClusterCenter>();

    @Override
    protected void reduce(ClusterCenter key, Iterable<Vector> values,
                          Context context) throws IOException, InterruptedException {

        Vector newCenter = new Vector();
        List<Vector> vectorList = new LinkedList<Vector>();
        int vectorSize = key.getCenter().getVector().length;
        newCenter.setVector(new double[vectorSize]);
        double[] resultVector = newCenter.getVector();

        for (Vector value : values) {
            vectorList.add(new Vector(value));
            double source[] = value.getVector();
            for (int i = 0; i < resultVector.length; i++) {
                resultVector[i] += source[i];
            }
        }

        for (int i = 0; i < resultVector.length; i++) {
            resultVector[i] = resultVector[i] / vectorList.size();
        }

        ClusterCenter center = new ClusterCenter(newCenter);
        centers.add(center);
        for (Vector vector : vectorList) {
            context.write(center, vector);
        }

        if (center.converged(key))
            context.getCounter(Counter.CONVERGED).increment(1);

    }

    @Override
    protected void cleanup(Context context) throws IOException,
            InterruptedException {
        super.cleanup(context);
        Configuration conf = context.getConfiguration();
        Path outPath = new Path(conf.get("centroid.path"));
        FileSystem fs = FileSystem.get(conf);
        fs.delete(outPath, true);
        final SequenceFile.Writer out = SequenceFile.createWriter(fs,
                context.getConfiguration(), outPath, ClusterCenter.class,
                IntWritable.class);
        final IntWritable value = new IntWritable(0);
        for (ClusterCenter center : centers) {
            out.append(center, value);
        }
        out.close();
    }
}