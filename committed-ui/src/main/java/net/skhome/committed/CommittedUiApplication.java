package net.skhome.committed;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@SpringBootApplication
@EnableZuulProxy
@EnableCircuitBreaker
@EnableDiscoveryClient
@EnableBinding(Source.class)
public class CommittedUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommittedUiApplication.class, args);
    }

}

@RestController
@RequestMapping("/commit-messages")
class CommitMessageGatewayController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    @Qualifier(Source.OUTPUT)
    private MessageChannel messageChannel;


    @RequestMapping(method = RequestMethod.GET)
    @HystrixCommand(fallbackMethod = "getCommitMessagesFallback")
    public Collection<String> getCommitMessages() {

        final ParameterizedTypeReference<Resources<CommitMessage>> ptr =
                new ParameterizedTypeReference<Resources<CommitMessage>>() {
                };

        Map<String, String> uriVariables = Collections.unmodifiableMap(Stream.of(
                new AbstractMap.SimpleEntry<>("page", "0"),
                new AbstractMap.SimpleEntry<>("size", "200")
        ).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));
        return restTemplate
                .exchange("http://committed-service/commitMessages?page={page}&size={size}", HttpMethod.GET, null, ptr, uriVariables)
                .getBody()
                .getContent()
                .stream()
                .map(CommitMessage::getMessage)
                .collect(Collectors.toList());
    }

    public Collection<String> getCommitMessagesFallback() {
        return Collections.singleton("default commit message");
    }

    @RequestMapping(method = RequestMethod.GET, value = "/random")
    public String getRandomCommitMessage() {
        Random rng = new Random();
        Collection<String> commitMessages = getCommitMessages();
        return StreamSupport.stream(commitMessages.spliterator(), false).skip(rng.nextInt(commitMessages.size())).findAny().get();
    }

    @RequestMapping(method = RequestMethod.POST)
    public void createCommitMessage(@RequestParam(name = "message") final String message) {
        messageChannel.send(MessageBuilder.withPayload(message).build());
    }

}


@Getter
@Setter
@ToString
class CommitMessage {

    private String message;

}
