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
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;

public class Streaming {

    public static  void main(String args[]){
         TupleTag<String> VALID_ROWS=new TupleTag<>(){};
        TupleTag<String> INVALID_ROWS=new TupleTag<>(){};
        DataflowPipelineOptions dataflowPipelineOptions= PipelineOptionsFactory.as(DataflowPipelineOptions.class);
        dataflowPipelineOptions.setJobName("StreamingIngestion");
        dataflowPipelineOptions.setProject("nttdata-c4e-bde");
        dataflowPipelineOptions.setRegion("europe-west4");
        dataflowPipelineOptions.setGcpTempLocation("gs://c4e-uc1-dataflow-temp-15/temp");
        dataflowPipelineOptions.setRunner(DataflowRunner.class);

        Pipeline pipeline= Pipeline.create(dataflowPipelineOptions);

        String dlqTopicName="projects/nttdata-c4e-bde/topics/uc1-dlq-topic-15";
        String subscriptionData="projects/nttdata-c4e-bde/subscriptions/uc1-input-topic-sub-15";
        PCollection<String> pubsubmessage=pipeline.apply("ReadMessage", PubsubIO.readStrings().fromSubscription(subscriptionData));

        //PCollection<String> pubsubmessage=pipeline.apply(PubsubIO.readStrings().fromTopic("projects/nttdata-c4e-bde/topics/uc1-input-topic-15"));


        PCollectionTuple rowcheck=pubsubmessage.apply(ParDo.of(new RowChecker()).withOutputTags(VALID_ROWS, TupleTagList.of(INVALID_ROWS)));
        PCollection<TableRow> bqrow=rowcheck.get(VALID_ROWS).apply(ParDo.of(new ConvertorStringBq()));
        bqrow.apply(BigQueryIO.writeTableRows().to("nttdata-c4e-bde.uc1_15.account")
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                        .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND));

        PCollection<String> invalidData=rowcheck.get(INVALID_ROWS);
        //write Invalid data(malformed data) to Big query.
        invalidData.apply("SendInValidDataToDLQ",PubsubIO.writeStrings().to(dlqTopicName));

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

    private static class RowChecker extends DoFn<String,String>{

        private static TupleTag<String> VALID_ROWS=new TupleTag<>(){};
        private static TupleTag<String> INVALID_ROWS=new TupleTag<>(){};
        @ProcessElement
        public void check(ProcessContext processContext){
            String[] arrJson=processContext.element().split(",");
            if(arrJson.length==3) {
                //validatios
                if(arrJson[0].contains("id") && arrJson[1].contains("name") &&arrJson[2].contains("surname")){
                    processContext.output(VALID_ROWS,processContext.element());
                }else{
                    //Malformed data
                    processContext.output(INVALID_ROWS,processContext.element());
                }
            }else{
                //Malformed data
                processContext.output(INVALID_ROWS,processContext.element());
            }

        }

    }
}
