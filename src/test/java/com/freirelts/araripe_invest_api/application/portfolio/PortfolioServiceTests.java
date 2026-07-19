package com.freirelts.araripe_invest_api.application.portfolio;

import com.freirelts.araripe_invest_api.application.portfolio.PortfolioService.PositionInput;
import com.freirelts.araripe_invest_api.domain.assets.Asset;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPosition;
import com.freirelts.araripe_invest_api.domain.portfolio.CustomerPositionThesisStatus;
import com.freirelts.araripe_invest_api.domain.portfolio.PositionStatus;
import com.freirelts.araripe_invest_api.domain.thesis.PositionThesis;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisStatus;
import com.freirelts.araripe_invest_api.domain.thesis.ThesisType;
import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.AssetRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.CustomerPositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.PositionThesisRepository;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@Import(PortfolioService.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioServiceTests {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@Autowired
	private PortfolioService portfolioService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private CustomerPositionRepository positionRepository;

	@Autowired
	private CustomerPositionThesisRepository positionThesisRepository;

	@Autowired
	private PositionThesisRepository thesisRepository;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Test
	void customerCanCreateListUpdateAndCloseOwnedPosition() {
		User customer = saveCustomer("portfolio-owner@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));

		var created = portfolioService.createPosition(customer.getId(), input(asset, "10", "38.40"));

		assertThat(created.symbol()).isEqualTo("WEGE3");
		assertThat(created.status()).isEqualTo(PositionStatus.OPEN);
		assertThat(portfolioService.listPositions(customer.getId())).hasSize(1);

		var updated = portfolioService.updatePosition(customer.getId(), created.id(),
				new PositionInput(asset.getId(), new BigDecimal("12"), new BigDecimal("37.50"),
						LocalDate.of(2026, 7, 7), new BigDecimal("31.00"), new BigDecimal("48.00"),
						new BigDecimal("0.280000"), "Ajuste de preco medio"));

		assertThat(updated.quantity()).isEqualByComparingTo("12");
		assertThat(updated.averagePrice()).isEqualByComparingTo("37.50");
		assertThat(updated.notes()).isEqualTo("Ajuste de preco medio");

		var closed = portfolioService.closePosition(customer.getId(), created.id());

		assertThat(closed.status()).isEqualTo(PositionStatus.CLOSED);
		assertThat(closed.closedAt()).isNotNull();
		assertThat(positionRepository.findById(created.id()).orElseThrow().isOpenAndValidForDailyScan()).isFalse();
	}

	@Test
	void registeringContributionUpdatesQuantityAndAveragePrice() {
		User customer = saveCustomer("portfolio-contribution@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		var created = portfolioService.createPosition(customer.getId(), input(asset, "100", "20.00"));

		var updated = portfolioService.registerContribution(customer.getId(), created.id(),
				new PortfolioService.ContributionInput(new BigDecimal("50"), new BigDecimal("24.00"),
						LocalDate.of(2026, 7, 8), "Aporte executado na corretora"));

		assertThat(updated.quantity()).isEqualByComparingTo("150");
		assertThat(updated.averagePrice()).isEqualByComparingTo("21.333333");
		assertThat(updated.notes()).isEqualTo("Aporte executado na corretora");
	}

	@Test
	void registeringReductionUpdatesQuantityAndPreservesAveragePrice() {
		User customer = saveCustomer("portfolio-reduction@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("WEGE3", "WEG S.A.", "Bens Industriais"));
		var created = portfolioService.createPosition(customer.getId(), input(asset, "100", "20.00"));

		var updated = portfolioService.registerReduction(customer.getId(), created.id(),
				new PortfolioService.ReductionInput(new BigDecimal("30"), LocalDate.of(2026, 7, 8),
						"Saida parcial registrada pelo cliente"));

		assertThat(updated.quantity()).isEqualByComparingTo("70");
		assertThat(updated.averagePrice()).isEqualByComparingTo("20.00");
		assertThat(updated.status()).isEqualTo(PositionStatus.OPEN);
		assertThat(updated.notes()).isEqualTo("Saida parcial registrada pelo cliente");
	}

	@Test
	void registeringFullReductionClosesPositionAndRejectsQuantityAboveCurrentPosition() {
		User customer = saveCustomer("portfolio-full-reduction@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("EGIE3", "Engie Brasil", "Utilidade Publica"));
		var created = portfolioService.createPosition(customer.getId(), input(asset, "40", "44.00"));

		assertThatThrownBy(() -> portfolioService.registerReduction(customer.getId(), created.id(),
				new PortfolioService.ReductionInput(new BigDecimal("41"), LocalDate.of(2026, 7, 8),
						"Quantidade maior que o registro atual")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400 BAD_REQUEST");

		var closed = portfolioService.registerReduction(customer.getId(), created.id(),
				new PortfolioService.ReductionInput(new BigDecimal("40"), LocalDate.of(2026, 7, 8),
						"Saida total registrada pelo cliente"));

		assertThat(closed.status()).isEqualTo(PositionStatus.CLOSED);
		assertThat(closed.quantity()).isEqualByComparingTo("40");
		assertThat(closed.closedAt()).isNotNull();
	}

	@Test
	void userCannotAccessPositionFromAnotherCustomer() {
		User owner = saveCustomer("portfolio-owner-deny@araripe.test");
		User other = saveCustomer("portfolio-other-deny@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("ITUB4", "Itau Unibanco PN", "Financeiro"));
		var created = portfolioService.createPosition(owner.getId(), input(asset, "100", "28.00"));

		assertThatThrownBy(() -> portfolioService.updatePosition(other.getId(), created.id(),
				input(asset, "120", "27.50")))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND");

		assertThatThrownBy(() -> portfolioService.closePosition(other.getId(), created.id()))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND");
	}

	@Test
	void customerAssociatesMainThesisOnlyWhenItReferencesTheSameAsset() {
		User customer = saveCustomer("portfolio-associate@araripe.test");
		Asset positionAsset = assetRepository.saveAndFlush(new Asset("PETR4", "Petrobras PN", "Energia"));
		Asset otherAsset = assetRepository.saveAndFlush(new Asset("VALE3", "Vale S.A.", "Materiais Basicos"));
		var position = portfolioService.createPosition(customer.getId(), input(positionAsset, "100", "37.00"));
		PositionThesis wrongAssetThesis = thesisRepository.saveAndFlush(thesis(otherAsset, LocalDate.of(2026, 7, 7),
				ThesisType.QUALITY_REASONABLE_PRICE, 80, "rules-v1"));
		PositionThesis sameAssetThesis = thesisRepository.saveAndFlush(thesis(positionAsset, LocalDate.of(2026, 7, 7),
				ThesisType.QUALITY_REASONABLE_PRICE, 82, "rules-v1"));

		assertThatThrownBy(() -> portfolioService.associateMainThesis(customer.getId(), position.id(),
				wrongAssetThesis.getId(), "Tese de outro ativo"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("422 UNPROCESSABLE_ENTITY");

		var associated = portfolioService.associateMainThesis(customer.getId(), position.id(),
				sameAssetThesis.getId(), "Tese principal aceita");

		assertThat(associated.accompaniedStudyModel()).isNotNull();
		assertThat(associated.accompaniedStudyModel().acceptedThesisId()).isEqualTo(sameAssetThesis.getId());
		assertThat(associated.accompaniedStudyModel().thesisType()).isEqualTo(ThesisType.QUALITY_REASONABLE_PRICE);
	}

	@Test
	void customerCannotAssociateInvalidOrDataBlockedMainThesis() {
		User customer = saveCustomer("portfolio-invalid-thesis@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("BBAS3", "Banco do Brasil", "Financeiro"));
		var position = portfolioService.createPosition(customer.getId(), input(asset, "100", "27.00"));
		PositionThesis ignoredThesis = thesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.QUALITY_REASONABLE_PRICE, 55, "rules-v1", ThesisStatus.DADOS_INSUFICIENTES));
			PositionThesis dataBlockedThesis = thesis(asset, LocalDate.of(2026, 7, 8),
					ThesisType.SUSTAINABLE_DIVIDENDS, 75, "rules-v1");
			dataBlockedThesis.setFailedFiltersJson("[{\"code\":\"DATA_QUALITY_BLOCKED\"}]");
			PositionThesis savedDataBlockedThesis = thesisRepository.saveAndFlush(dataBlockedThesis);

		assertThatThrownBy(() -> portfolioService.associateMainThesis(customer.getId(), position.id(),
				ignoredThesis.getId(), "Tese ignorada"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("422 UNPROCESSABLE_ENTITY");
			assertThatThrownBy(() -> portfolioService.associateMainThesis(customer.getId(), position.id(),
					savedDataBlockedThesis.getId(), "Tese com dados bloqueados"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("422 UNPROCESSABLE_ENTITY");
		assertThat(positionThesisRepository.findByPositionIdOrderByCreatedAtDesc(position.id())).isEmpty();
	}

	@Test
	void replacingMainThesisClosesPreviousAssociationAndKeepsHistory() {
		User customer = saveCustomer("portfolio-replace@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("EGIE3", "Engie Brasil", "Utilidade Publica"));
		var position = portfolioService.createPosition(customer.getId(), input(asset, "30", "40.00"));
		PositionThesis first = thesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.SUSTAINABLE_DIVIDENDS, 76, "rules-v1"));
		PositionThesis second = thesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 8),
				ThesisType.PROFITABLE_GROWTH_HEALTHY_TREND, 81, "rules-v1"));

		portfolioService.associateMainThesis(customer.getId(), position.id(), first.getId(), "Tese inicial");
		var replaced = portfolioService.replaceMainThesis(customer.getId(), position.id(), second.getId(),
				"Tese revisada pelo cliente");

		assertThat(replaced.accompaniedStudyModel().acceptedThesisId()).isEqualTo(second.getId());
		var history = positionThesisRepository.findByPositionIdOrderByCreatedAtDesc(position.id());
		assertThat(history).hasSize(2);
		assertThat(history).anySatisfy(association -> {
			assertThat(association.getAcceptedThesis().getId()).isEqualTo(first.getId());
			assertThat(association.getStatus()).isEqualTo(CustomerPositionThesisStatus.CLOSED);
			assertThat(association.getExitReason()).isEqualTo("Tese principal substituida pelo cliente.");
		});
		assertThat(history).anySatisfy(association -> {
			assertThat(association.getAcceptedThesis().getId()).isEqualTo(second.getId());
			assertThat(association.getStatus()).isEqualTo(CustomerPositionThesisStatus.ACTIVE);
		});
	}

	@Test
	void closingPositionAlsoClosesActiveMainThesis() {
		User customer = saveCustomer("portfolio-close-thesis@araripe.test");
		Asset asset = assetRepository.saveAndFlush(new Asset("TAEE11", "Taesa UNT", "Utilidade Publica"));
		var position = portfolioService.createPosition(customer.getId(), input(asset, "20", "35.00"));
		PositionThesis thesis = thesisRepository.saveAndFlush(thesis(asset, LocalDate.of(2026, 7, 7),
				ThesisType.SUSTAINABLE_DIVIDENDS, 78, "rules-v1"));
		portfolioService.associateMainThesis(customer.getId(), position.id(), thesis.getId(), "Tese de dividendos");

		portfolioService.closePosition(customer.getId(), position.id());

		CustomerPosition persisted = positionRepository.findById(position.id()).orElseThrow();
		assertThat(persisted.getStatus()).isEqualTo(PositionStatus.CLOSED);
		assertThat(positionThesisRepository.findByPositionIdAndStatus(position.id(),
				CustomerPositionThesisStatus.ACTIVE)).isEmpty();
		assertThat(positionThesisRepository.findByPositionIdOrderByCreatedAtDesc(position.id()))
				.singleElement()
				.satisfies(association -> {
					assertThat(association.getStatus()).isEqualTo(CustomerPositionThesisStatus.CLOSED);
					assertThat(association.getExitReason()).isEqualTo("Posicao encerrada pelo cliente.");
				});
	}

	private User saveCustomer(String email) {
		User user = new User("Cliente", email, "{bcrypt}hash", SubscriptionStatus.ACTIVE);
		user.addRole(UserRoleType.CUSTOMER);
		return userRepository.saveAndFlush(user);
	}

	private PositionInput input(Asset asset, String quantity, String averagePrice) {
		return new PositionInput(asset.getId(), new BigDecimal(quantity), new BigDecimal(averagePrice),
				LocalDate.of(2026, 7, 7), new BigDecimal("31.00"), new BigDecimal("48.00"),
				new BigDecimal("0.250000"), "Posicao cadastrada pelo cliente");
	}

	private PositionThesis thesis(Asset asset, LocalDate referenceDate, ThesisType thesisType, int score,
			String ruleVersion) {
		return thesis(asset, referenceDate, thesisType, score, ruleVersion, ThesisStatus.CRITERIOS_ATENDIDOS);
	}

	private PositionThesis thesis(Asset asset, LocalDate referenceDate, ThesisType thesisType, int score,
			String ruleVersion, ThesisStatus status) {
		PositionThesis thesis = new PositionThesis(asset, referenceDate, thesisType, status, score, ruleVersion);
		thesis.setPriceCeiling(new BigDecimal("42.00"));
		thesis.setFairPriceEstimate(new BigDecimal("49.40"));
		thesis.setSafetyMarginPercent(new BigDecimal("0.150000"));
		return thesis;
	}
}
