package by.bsuir.repair.runtime;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaInfrastructure {
    @Bean KafkaAdmin.NewTopics repairTopics(){
        NewTopic[] values=new NewTopic[EventTopics.ALL.length*2]; int i=0;
        for(String topic:EventTopics.ALL){values[i++]=TopicBuilder.name(topic).partitions(3).replicas(1).build();values[i++]=TopicBuilder.name(topic+".DLT").partitions(1).replicas(1).build();}
        return new KafkaAdmin.NewTopics(values);
    }
    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String,String> template){return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template),new FixedBackOff(500,2));}
}
