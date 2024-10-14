package com.uinme.mes.callmonitor;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.file.dsl.Files;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.uinme.mes.callmonitor.model.CsvModel;

@SpringBootApplication
public class CallMonitor {
    public static void main(String[] args) {
        new SpringApplicationBuilder(CallMonitor.class)
            .web(WebApplicationType.NONE)
            .run(args);
    }

    /**
     * 対象ディレクトリのファイルを監視するフロー
     * - 処理したファイルは削除しない
     * - 処理したファイルは1回のみ処理され、重複して処理しない
     *   ただし、アプリケーションを再起動した場合、すでに対象ディレクトリに存在するファイルは
     *   すべて処理される(処理したくない場合、運用で起動前にディレクトリを空にする。未処理ファイル
     *   を調べる必要あり)
     * 
     * @param inputDir
     * @param delayPolling
     * @param charset
     * @return
     */
    @Bean
    IntegrationFlow fileMonitorFlow(
        @Value("${callmonitor.input_dir}") String inputDir,
        @Value("${callmonitor.monitoring_interval}") long interval,
        @Value("${callmonitor.charset}") String charset
    ) {
        return IntegrationFlows
            .from(
                Files.inboundAdapter(new File(inputDir))
                    // 監視対象のディレクトリが存在しない場合、自動で作成するか
                    .autoCreateDirectory(false)
                    // サブディレクトリの監視を対象にするか
                    .recursive(false)
                    // 重複処理を防止するか(一度処理したファイルを2回目以降処理しないようにする)
                    .preventDuplicates(true)
                    // OS依存の監視サービスを利用するか
                    //   OS側のファイル更新イベントキューを利用し、そこからアプリケーションがキューを取得する。
                    //   OS側の処理速度にアプリケーション側の処理速度が追いつかない場合、キューはオーバーフローする。
                    //   オーバーフローした場合、更新イベントは失われる。
                    //   このため、OS依存の監視サービスは利用せず、アプリケーション側で監視処理を行う。
                    .useWatchService(false),
                c -> c.poller(Pollers.fixedDelay(interval))
            )
            .transform(Files.toStringTransformer(charset))
            .channel("ReadFileChannel")
            .get();
    }

    @Bean
    IntegrationFlow transformToPojoFlow(
        @Value("${callmonitor.csv_header}") boolean withHeader,
        @Value("${callmonitor.charset}") String charset
    ) throws UnsupportedEncodingException {
        return IntegrationFlows
            .from("ReadFileChannel")
            .transform(Transformers.converter(source -> {
                CsvMapper csvMapper = new CsvMapper();

                CsvSchema csvSchema = csvMapper.schemaFor(CsvModel.class);
                if (withHeader) {
                    csvSchema.withHeader();
                } else {
                    csvSchema.withoutHeader();
                }

                MappingIterator<CsvModel> mappingIterator;
                try {
                    mappingIterator = csvMapper.readerFor(CsvModel.class)
                        .with(csvSchema)
                        .readValues(source.toString().getBytes(charset));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                List<CsvModel> csvModels = new ArrayList<CsvModel>();
                while (mappingIterator.hasNext()) {
                    csvModels.add(mappingIterator.next());
                }

                return csvModels;
            }))
            .channel("TransformFileContentChannel")
            .get();
    }
    
//    @Bean
//    @Autowired
//    IntegrationFlow insertToDatabaseFlow(com.uinme.mes.callmonitor.mapper.CsvMapper mapper) {
//        return IntegrationFlows
//            .from("TransformFileContentChannel")
//            .handle(message -> {
//                List<CsvModel> csvModels = (List<CsvModel>)message.getPayload();
//                for(CsvModel csvModel : csvModels) {
//                    mapper.insert(csvModel);
//                }
//            })
//            .get();
//    }
    
    @Bean
    @Autowired
    IntegrationFlow sendRabbitMqFlow(AmqpTemplate amqpTemplate) {
        return IntegrationFlows
            .from("ReadFileChannel")
            .handle(Amqp.outboundAdapter(amqpTemplate))
            .get();
    }

//    @Bean
//    IntegrationFlow outputStdout() {
//        return IntegrationFlows
//            .from("TransformFileContentChannel")
//            .handle(System.out::println)
//            .get();
//    }
}
