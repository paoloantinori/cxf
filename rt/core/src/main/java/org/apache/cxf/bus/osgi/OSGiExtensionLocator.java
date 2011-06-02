/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.bus.osgi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.cxf.bus.extension.Extension;
import org.apache.cxf.bus.extension.ExtensionFragmentParser;
import org.apache.cxf.bus.extension.ExtensionRegistry;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;

/**
 * 
 */
public class OSGiExtensionLocator implements BundleActivator, SynchronousBundleListener {
    private ConcurrentMap<Long, List<Extension>> extensions 
        = new ConcurrentHashMap<Long, List<Extension>>();
    private long id;

    /** {@inheritDoc}*/
    public void bundleChanged(BundleEvent event) {
        if (event.getType() == BundleEvent.RESOLVED && id != event.getBundle().getBundleId()) {
            try {
                register(event.getBundle());
            } catch (Exception ex) {
                //ignore
            }
        } else if (event.getType() == BundleEvent.UNRESOLVED || event.getType() == BundleEvent.UNINSTALLED) {
            unregister(event.getBundle().getBundleId());
        }
    }

    /** {@inheritDoc}*/
    public void start(BundleContext context) throws Exception {
        context.addBundleListener(this);
        id = context.getBundle().getBundleId();
        for (Bundle bundle : context.getBundles()) {
            if ((bundle.getState() == Bundle.RESOLVED 
                || bundle.getState() == Bundle.STARTING 
                || bundle.getState() == Bundle.ACTIVE 
                || bundle.getState() == Bundle.STOPPING)
                && bundle.getBundleId() != context.getBundle().getBundleId()) {
                register(bundle);
            }
        }
    }

    /** {@inheritDoc}*/
    public void stop(BundleContext context) throws Exception {
        context.removeBundleListener(this);
        while (!extensions.isEmpty()) {
            unregister(extensions.keySet().iterator().next());
        }
    }

    
    protected void register(final Bundle bundle) throws IOException {
        List<Extension> list = extensions.get(bundle.getBundleId());
        Enumeration e = bundle.findEntries("META-INF/cxf/", "bus-extensions.txt", false);
        if (e != null) {
            while (e.hasMoreElements()) {
                final URL u = (URL)e.nextElement();
                InputStream ins = u.openStream();
                List<Extension> orig = new ExtensionFragmentParser()
                    .getExtensionsFromText(ins);
                ins.close();

                if (orig != null && !orig.isEmpty()) {
                    if (list == null) {
                        list = new CopyOnWriteArrayList<Extension>();
                        extensions.put(bundle.getBundleId(), list);
                    }
                    for (Extension ext : orig) {
                        list.add(new OSGiExtension(ext, bundle));
                    }
                    ExtensionRegistry.addExtensions(list);
                }
            }
        }
    }
    protected void unregister(final long bundleId) {
        List<Extension> list = extensions.remove(bundleId);
        if (list != null) {
            ExtensionRegistry.removeExtensions(list);
        }
    }
    public class OSGiExtension extends Extension {
        final Bundle bundle;
        public OSGiExtension(Extension e, Bundle b) {
            super(e);
            bundle = b;
        }
        public Class<?> getClassObject(ClassLoader cl) {
            if (clazz == null) {
                try {
                    clazz = bundle.loadClass(className);
                } catch (ClassNotFoundException e) {
                    //ignore, fall to super
                }
            }
            return super.getClassObject(cl);
        }
        public Class<?> loadInterface(ClassLoader cl) {
            try {
                return bundle.loadClass(interfaceName);
            } catch (ClassNotFoundException e) {
                //ignore, fall to super
            }
            return super.loadInterface(cl);
        }
    }

}
