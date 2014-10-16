/*
 * Copyright (C) 2014 ddecap
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

package be.ugent.intec.halvade.hadoop.mapreduce;

import fi.tkk.ics.hadoop.bam.SAMRecordWritable;
import be.ugent.intec.halvade.hadoop.datatypes.ChromosomeRegion;
import java.io.IOException;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import be.ugent.intec.halvade.tools.BWAMemInstance;
import be.ugent.intec.halvade.utils.Logger;
import be.ugent.intec.halvade.utils.MyConf;
import java.io.File;
import java.net.URISyntaxException;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;

/**
 *
 * @author ddecap
 */
// TODO use compressed SAMrecod as output
public class BWAMemMapper extends Mapper<LongWritable, Text, ChromosomeRegion, SAMRecordWritable> {
    private BWAMemInstance instance;
    private int count, readcount;
    private boolean reuseJVM;

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        super.cleanup(context);
        Logger.DEBUG(count + "fastq reads processed");
        Logger.DEBUG("starting cleanup");
        // check if its the last on this node, if so close it:
        // assumes the jvm is reused fully (mapred.job.reuse.jvm.num.tasks = -1)
        try {
            if (!reuseJVM || MyConf.allTasksCompleted(context.getConfiguration())) {
                Logger.DEBUG("closing BWA");
                instance.closeBWA();
            }
        } catch (URISyntaxException ex) {
            Logger.EXCEPTION(ex);
            throw new InterruptedException();
        }
        Logger.DEBUG("finished cleanup");
    }
    

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        instance.feedLine(value.toString());
        count++;
        if(count % 4 == 0) {
            context.getCounter(HalvadeCounters.IN_BWA_READS).increment(1);
            readcount++;
        }
    }

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        super.setup(context);
        try {
            reuseJVM = MyConf.getReuseJVM(context.getConfiguration());
            String binDir = checkBinaries(context); 
            instance = BWAMemInstance.getBWAInstance(context, binDir);
            Logger.DEBUG("bwa startup state: " + instance.getState());
            count = 0;
            readcount = 0;
            // add a file to distributed cache representing this task
            String taskId = context.getTaskAttemptID().toString();
            Logger.DEBUG("taskId = " + taskId);
            MyConf.addTaskRunning(context.getConfiguration(), taskId);
        } catch (URISyntaxException ex) {
            Logger.EXCEPTION(ex);
            throw new InterruptedException();
        }
    }
    
    protected String checkBinaries(Context context) throws IOException {
        Logger.DEBUG("Checking for binaries...");
        String binDir = MyConf.getBinDir(context.getConfiguration());
        if(binDir != null) {
            return binDir;
        }
        Path[] localPath = context.getLocalCacheArchives();
        for(int i = 0; i < localPath.length; i++ ) {
            if(localPath[i].getName().equals("bin.tar.gz")) {
                binDir = localPath[i] + "/bin/";
            }
        }
        printDirectoryTree(new File(binDir), 0);
        return binDir;
    }
    protected void printDirectoryTree(File dir, int level) {
        String whitespace = "";
        for(int i = 0; i < level; i++)
            whitespace += "\t";
        File[] list = dir.listFiles();
        if(list != null) {
            for(int i = 0; i < list.length; i++ ) {
                String attr = "";
                if(list[i].isDirectory()) 
                    attr += "D ";
                else 
                    attr += "F ";
                if(list[i].canExecute()) 
                    attr += "E ";
                else 
                    attr += "NE ";
                if(list[i].canRead()) 
                    attr += "R ";
                else 
                    attr += "NR ";
                if(list[i].canWrite()) 
                    attr += "W ";
                else 
                    attr += "NW ";
                Logger.DEBUG(whitespace + attr + "\t" + list[i].getName());
                if(list[i].isDirectory())
                    printDirectoryTree(list[i], level + 1);
            }
        } else {
                    Logger.DEBUG(whitespace + "N");
        }
    }
}
