import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.runners.dataflow.DataflowRunner;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;

public class Streaming {

    public static  void main(String args[]){
        DataflowPipelineOptions dataflowPipelineOptions= PipelineOptionsFactory.as(DataflowPipelineOptions.class);
        dataflowPipelineOptions.setJobName("StreamingIngestion");
        dataflowPipelineOptions.setProject("nttdata-c4e-bde");
        dataflowPipelineOptions.setRegion("europe-west4");
        dataflowPipelineOptions.setGcpTempLocation("gs://c4e-uc1-dataflow-temp-15/temp");
        dataflowPipelineOptions.setRunner(DataflowRunner.class);
        Pipeline pipeline= Pipeline.create(dataflowPipelineOptions);
        PCollection<String> pubsubmessage=pipeline.apply(PubsubIO.readStrings().fromTopic("projects/nttdata-c4e-bde/topics/uc1-input-topic-15"));
        PCollection<TableRow> bqrow=pubsubmessage.apply(ParDo.of(new ConvertorStringBq()));
        bqrow.apply(BigQueryIO.writeTableRows().to("nttdata-c4e-bde.uc1_15.account")
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                        .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND));

        pipeline.run();
    }
    public static class ConvertorStringBq extends DoFn<String,TableRow>{
        @ProcessElement
        public void processing(ProcessContext processContext)
        {
            TableRow tableRow=new TableRow().set("id",processContext.element().hashCode())
                            .set("name",processContext.element().toString())
                                    .set("surname",processContext.element().toString());
            processContext.output(tableRow);
        }



    }
}
