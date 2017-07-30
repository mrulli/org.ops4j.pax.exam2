/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.exam.container.eclipse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.container.eclipse.EclipseApplication.EclipseApplicationProvision;
import org.ops4j.pax.exam.container.eclipse.EclipseArtifactSource.EclipseBundleSource;
import org.ops4j.pax.exam.container.eclipse.EclipseArtifactSource.EclipseFeatureSource;
import org.ops4j.pax.exam.container.eclipse.EclipseArtifactSource.EclipseProjectSource;
import org.ops4j.pax.exam.container.eclipse.EclipseArtifactSource.EclipseUnitSource;
import org.ops4j.pax.exam.container.eclipse.impl.CombinedSource;
import org.ops4j.pax.exam.container.eclipse.impl.DefaultEclipseProvision;
import org.ops4j.pax.exam.container.eclipse.impl.EclipseApplicationImpl;
import org.ops4j.pax.exam.container.eclipse.impl.parser.ProductParser;
import org.ops4j.pax.exam.container.eclipse.impl.sources.directory.DirectoryResolver;
import org.ops4j.pax.exam.container.eclipse.impl.sources.feature.FeatureResolver;
import org.ops4j.pax.exam.container.eclipse.impl.sources.p2repository.P2Resolver;
import org.ops4j.pax.exam.container.eclipse.impl.sources.target.TargetResolver;
import org.ops4j.pax.exam.container.eclipse.impl.sources.workspace.WorkspaceResolver;

/**
 * Static Options to configure the EclipseContainer
 * 
 * @author Christoph Läubrich
 *
 */
public class EclipseOptions {

    private static final class EclipseLauncherImpl implements EclipseLauncher {

        private final class EclipseProductImplementation implements EclipseProduct {

            private final String productID;

            private EclipseProductImplementation(String productID) {
                this.productID = productID;
            }

            @Override
            public EclipseApplication application(String applicationID) {
                return applicationInternal(applicationID, null);
            }

            private EclipseApplicationImpl applicationInternal(String applicationID,
                EclipseProvision provision) {
                return new EclipseApplicationImpl(EclipseLauncherImpl.this, false, provision,
                    CoreOptions.frameworkProperty("eclipse.application").value(applicationID),
                    CoreOptions.frameworkProperty("eclipse.product").value(productID));
            }
        }

        private final boolean forked;

        private EclipseLauncherImpl(boolean forked) {
            this.forked = forked;
        }

        @Override
        public boolean isForked() {
            return forked;
        }

        @Override
        public EclipseApplicationImpl ignoreApp() {
            return ignoreAppInternal(null);
        }

        private EclipseApplicationImpl ignoreAppInternal(EclipseProvision provision) {
            return new EclipseApplicationImpl(this, true, provision,
                CoreOptions.frameworkProperty("eclipse.ignoreApp").value(true));
        }

        @Override
        public EclipseApplication application(String applicationID) {
            return applicationInternal(applicationID, null);
        }

        private EclipseApplicationImpl applicationInternal(String applicationID,
            EclipseProvision provision) {
            return new EclipseApplicationImpl(this, false, provision,
                CoreOptions.frameworkProperty("eclipse.application").value(applicationID));
        }

        @Override
        public EclipseApplicationProvision productDefinition(InputStream productFile,
            final EclipseArtifactSource source, String... ignoreItems) throws IOException {
            DefaultEclipseProvision provision = createDefaultProvision(source, ignoreItems);
            ProductParser parser = new ProductParser(productFile);
            provision.product(parser);
            String productID = parser.getProductID();
            String application = parser.getApplication();
            if (productID != null) {
                EclipseProductImplementation product = product(productID);
                if (application != null) {
                    return product.applicationInternal(application, provision);
                }
            }
            else if (application != null) {
                return applicationInternal(application, provision);
            }
            return ignoreAppInternal(provision);
        }

        @Override
        public EclipseProductImplementation product(final String productID) {
            return new EclipseProductImplementation(productID);
        }

    }

    public static EclipseLauncher launcher(final boolean forked) {
        return new EclipseLauncherImpl(forked);
    }

    /**
     * Uses a Installation-Folder to provision bundles from
     * 
     * @param folder
     * @return
     * @throws IOException
     */
    public static EclipseInstallation fromInstallation(final File baseFolder) throws IOException {
        return new DirectoryResolver(baseFolder);
    }

    /**
     * Use an Eclipse-WOrkspace to provision bundles from
     * 
     * @param workspaceFolder
     * @return
     * @throws IOException
     */
    public static EclipseWorkspace fromWorkspace(final File workspaceFolder) throws IOException {
        return new WorkspaceResolver(workspaceFolder);
    }

    public static EclipseTargetPlatform fromTarget(InputStream targetDefinition)
        throws IOException {
        return new TargetResolver(targetDefinition);
    }

    public static EclipseRepository createRepository(URL url, String name) throws IOException {
        return new P2Resolver(name, url);
    }

    public static <Source extends EclipseBundleSource & EclipseFeatureSource> EclipseBundleSource fromFeatures(
        Source source, EclipseFeature... features) throws ArtifactNotFoundException, IOException {
        return fromFeatures(source, source, features);
    }

    public static EclipseBundleSource fromFeatures(EclipseBundleSource bundleSource,
        EclipseFeatureSource featureSource, EclipseFeature... features)
        throws ArtifactNotFoundException, IOException {
        List<EclipseFeatureOption> bootFeatures = new ArrayList<>();
        for (EclipseFeature feature : features) {
            if (feature instanceof EclipseFeatureOption) {
                bootFeatures.add((EclipseFeatureOption) feature);
            }
            else {
                bootFeatures.add(featureSource.feature(feature.getId()));
            }
        }
        return new FeatureResolver(bundleSource, featureSource, bootFeatures);
    }

    public static CombinedEclipseArtifactSource combine(final EclipseArtifactSource... sources) {
        return new CombinedSource(Arrays.asList(sources));

    }

    public static EclipseProvision provision(final EclipseArtifactSource source,
        String... ignoreItems) {
        return createDefaultProvision(source, ignoreItems);
    }

    private static DefaultEclipseProvision createDefaultProvision(
        final EclipseArtifactSource source, String... ignoreItems) {
        final Set<String> ignored = new HashSet<>();
        if (ignoreItems != null) {
            ignored.addAll(Arrays.asList(ignoreItems));
        }
        // We provide this by default
        ignored.add("org.eclipse.osgi");
        // We do the job of the configurator
        ignored.add("org.eclipse.equinox.simpleconfigurator");
        return new DefaultEclipseProvision(source, ignored);
    }

    public static interface CombinedEclipseArtifactSource
        extends EclipseFeatureSource, EclipseProjectSource, EclipseUnitSource, EclipseBundleSource {

    }

}