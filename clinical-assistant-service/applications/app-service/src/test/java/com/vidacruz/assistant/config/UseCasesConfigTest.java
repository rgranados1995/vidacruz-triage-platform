package com.vidacruz.assistant.config;

import com.vidacruz.assistant.model.answer.gateways.AnswerGenerator;
import com.vidacruz.assistant.model.protocol.gateways.ProtocolRetriever;
import com.vidacruz.assistant.model.urgency.gateways.UrgencyStateRepository;
import com.vidacruz.assistant.usecase.answerclinicalquestion.AnswerClinicalQuestionUseCase;
import com.vidacruz.assistant.usecase.urgencystate.GetUrgencyStateUseCase;
import com.vidacruz.assistant.usecase.urgencystate.ProjectTriageEventUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UseCasesConfigTest {

    @Test
    void shouldRegisterEveryUseCaseOfTheDomainAsABean() {
        new ApplicationContextRunner()
                .withUserConfiguration(UseCasesConfig.class)
                .withBean(ProtocolRetriever.class, () -> mock(ProtocolRetriever.class))
                .withBean(AnswerGenerator.class, () -> mock(AnswerGenerator.class))
                .withBean(UrgencyStateRepository.class, () -> mock(UrgencyStateRepository.class))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(AnswerClinicalQuestionUseCase.class)
                        .hasSingleBean(ProjectTriageEventUseCase.class)
                        .hasSingleBean(GetUrgencyStateUseCase.class));
    }
}
