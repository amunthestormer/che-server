/*
 * Copyright (c) 2012-2024 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.factory.server.bitbucket;

import static org.eclipse.che.api.factory.shared.Constants.URL_PARAMETER_NAME;
import static org.eclipse.che.dto.server.DtoFactory.newDto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.che.api.core.ApiException;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.factory.server.BaseFactoryParameterResolver;
import org.eclipse.che.api.factory.server.FactoryParametersResolver;
import org.eclipse.che.api.factory.server.scm.AuthorisationRequestManager;
import org.eclipse.che.api.factory.server.scm.PersonalAccessTokenManager;
import org.eclipse.che.api.factory.server.urlfactory.RemoteFactoryUrl;
import org.eclipse.che.api.factory.server.urlfactory.URLFactoryBuilder;
import org.eclipse.che.api.factory.shared.dto.FactoryDevfileV2Dto;
import org.eclipse.che.api.factory.shared.dto.FactoryMetaDto;
import org.eclipse.che.api.factory.shared.dto.FactoryVisitor;
import org.eclipse.che.api.factory.shared.dto.ScmInfoDto;
import org.eclipse.che.api.workspace.server.devfile.URLFetcher;

/** Provides Factory Parameters resolver for bitbucket repositories. */
@Singleton
public class BitbucketFactoryParametersResolver extends BaseFactoryParameterResolver
    implements FactoryParametersResolver {

  private static final String PROVIDER_NAME = "bitbucket";

  /** Parser which will allow to check validity of URLs and create objects. */
  private final BitbucketURLParser bitbucketURLParser;

  private final URLFetcher urlFetcher;
  /** Builder allowing to build objects from bitbucket URL. */
  private final BitbucketSourceStorageBuilder bitbucketSourceStorageBuilder;

  private final URLFactoryBuilder urlFactoryBuilder;

  /** Personal Access Token manager used when fetching protected content. */
  private final PersonalAccessTokenManager personalAccessTokenManager;

  private final BitbucketApiClient bitbucketApiClient;

  @Inject
  public BitbucketFactoryParametersResolver(
      BitbucketURLParser bitbucketURLParser,
      URLFetcher urlFetcher,
      BitbucketSourceStorageBuilder bitbucketSourceStorageBuilder,
      URLFactoryBuilder urlFactoryBuilder,
      PersonalAccessTokenManager personalAccessTokenManager,
      BitbucketApiClient bitbucketApiClient,
      AuthorisationRequestManager authorisationRequestManager) {
    super(authorisationRequestManager, urlFactoryBuilder, PROVIDER_NAME);
    this.bitbucketURLParser = bitbucketURLParser;
    this.urlFetcher = urlFetcher;
    this.bitbucketSourceStorageBuilder = bitbucketSourceStorageBuilder;
    this.urlFactoryBuilder = urlFactoryBuilder;
    this.personalAccessTokenManager = personalAccessTokenManager;
    this.bitbucketApiClient = bitbucketApiClient;
  }

  /**
   * Check if this resolver can be used with the given parameters.
   *
   * @param factoryParameters map of parameters dedicated to factories
   * @return true if it will be accepted by the resolver implementation or false if it is not
   *     accepted
   */
  @Override
  public boolean accept(@NotNull final Map<String, String> factoryParameters) {
    // Check if url parameter is a bitbucket URL
    return factoryParameters.containsKey(URL_PARAMETER_NAME)
        && bitbucketURLParser.isValid(factoryParameters.get(URL_PARAMETER_NAME));
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  /**
   * Create factory object based on provided parameters
   *
   * @param factoryParameters map containing factory data parameters provided through URL
   * @throws BadRequestException when data are invalid
   */
  @Override
  public FactoryMetaDto createFactory(@NotNull final Map<String, String> factoryParameters)
      throws ApiException {
    // no need to check null value of url parameter as accept() method has performed the check
    final BitbucketUrl bitbucketUrl =
        bitbucketURLParser.parse(factoryParameters.get(URL_PARAMETER_NAME));
    // create factory from the following location if location exists, else create default factory
    return createFactory(
        factoryParameters,
        bitbucketUrl,
        new BitbucketFactoryVisitor(bitbucketUrl),
        new BitbucketAuthorizingFileContentProvider(
            bitbucketUrl, urlFetcher, personalAccessTokenManager, bitbucketApiClient));
  }

  /**
   * Visitor that puts the default devfile or updates devfile projects into the Bitbucket Factory,
   * if needed.
   */
  private class BitbucketFactoryVisitor implements FactoryVisitor {

    private final BitbucketUrl bitbucketUrl;

    private BitbucketFactoryVisitor(BitbucketUrl bitbucketUrl) {
      this.bitbucketUrl = bitbucketUrl;
    }

    @Override
    public FactoryDevfileV2Dto visit(FactoryDevfileV2Dto factoryDto) {
      ScmInfoDto scmInfo =
          newDto(ScmInfoDto.class)
              .withScmProviderName(bitbucketUrl.getProviderName())
              .withRepositoryUrl(bitbucketUrl.repositoryLocation());
      if (bitbucketUrl.getBranch() != null) {
        scmInfo.withBranch(bitbucketUrl.getBranch());
      }
      return factoryDto.withScmInfo(scmInfo);
    }
  }

  @Override
  public RemoteFactoryUrl parseFactoryUrl(String factoryUrl) {
    return bitbucketURLParser.parse(factoryUrl);
  }
}
