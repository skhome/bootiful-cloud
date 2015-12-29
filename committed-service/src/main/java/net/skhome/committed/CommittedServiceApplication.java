/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.skhome.committed;

import com.google.common.io.Files;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;

@EnableDiscoveryClient
@SpringBootApplication
// @IntegrationComponentScan
// @EnableBinding(Sink.class)
public class CommittedServiceApplication implements CommandLineRunner {

    @Value("classpath:messages.txt")
    private Resource messagesResource;

    @Autowired
    private CommitMessageRepository repository;

    public static void main(final String[] args) {
        SpringApplication.run(CommittedServiceApplication.class, args);
    }

    @Override
    public void run(final String... args) throws IOException {
        Files
            .readLines(messagesResource.getFile(), Charset.forName("UTF-8"))
            .forEach(message -> repository.save(new CommitMessage(message)));
    }

    @Bean
    HealthIndicator healthIndicator() {
        return () -> Health.status("I <3 Spring!").build();
    }
}

//@MessageEndpoint
//class CommitMessageProcessor {
//
//    @Autowired
//    private CommitMessageRepository repository;
//
//    @ServiceActivator(inputChannel = Sink.INPUT)
//    public void receiveNewCommitMessage(final String message) {
//        System.out.println("receiving new message: " + message);
//        repository.save(new CommitMessage(message));
//    }
//
//}


@RefreshScope
@RestController
class WelcomeRestController {

    @Value("${info.message}")
    private String message;

    @RequestMapping(method = RequestMethod.GET, value = "/welcome")
    public String welcomeMessage() {
        return this.message;
    }

}

@RepositoryRestResource
interface CommitMessageRepository extends JpaRepository<CommitMessage, Long> {

    @RestResource(rel = "by-content", path = "by-content")
    Collection<CommitMessage> findByMessageContaining(@Param("content") String content);

}

@Entity
@Getter
@ToString
class CommitMessage {

    @Id
    @GeneratedValue
    private String id;

    @Setter
    private String message;

    CommitMessage() {
        // JPA, why do I have to?
    }

    public CommitMessage(final String message) {
        this.message = message;
    }

}
