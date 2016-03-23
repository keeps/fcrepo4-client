/**
 * Copyright 2015 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.client.impl;

import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;

import org.apache.commons.io.IOUtils;
import org.fcrepo.client.FedoraContent;
import org.fcrepo.client.FedoraDatastream;
import org.fcrepo.client.FedoraException;
import org.fcrepo.client.FedoraObject;
import org.fcrepo.client.FedoraRepository;
import org.fcrepo.client.FedoraResource;
import org.fcrepo.client.NotFoundException;
import org.fcrepo.kernel.api.RdfLexicon;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Mike Durbin
 * @author Hélder Silva
 */
public class FedoraRepositoryImplIT {

    final static Logger LOGGER = getLogger(FedoraRepositoryImplTest.class);

    public static String FEDORA_CONTEXT = "fcrepo-webapp";

    private static String CARGO_PORT = System.getProperty("fcrepo.dynamic.test.port", "8080");

    private static String getFedoraBaseUrl() {
        return "http://localhost:" + CARGO_PORT + "/" + FEDORA_CONTEXT;
    }

    private FedoraRepository repo;

    @Before
    public void setUp() throws FedoraException {
        repo = new FedoraRepositoryImpl(getFedoraBaseUrl() + "/rest/");
    }

    @Test
    public void testBasicResourceCreation() throws IOException, FedoraException {
        final String path = getRandomUniqueId();

        repo.createObject(path);

        final FedoraObject object = repo.getObject(path);

        final String content = "Test String";
        final String dsid = getRandomUniqueId();
        final String dsPath = path + "/" + dsid;

        repo.createDatastream(dsPath, getStringTextContent(content));

        final FedoraDatastream datastream = repo.getDatastream(dsPath);
    }

    @Test
    public void testPidMintedResourceCreation() throws IOException, FedoraException {
        final FedoraObject object = repo.createResource("");
        Assert.assertTrue("A newly created object should exist at the path given by the createObject() call.",
                repo.exists(object.getPath()));

        final FedoraObject containedObject = object.createObject();
        Assert.assertTrue("A newly created object should exist at the path given by the createObject() call.",
                repo.exists(containedObject.getPath()));
        Assert.assertTrue("The new object's path should start with the containing object's path.",
                containedObject.getPath().startsWith(object.getPath()));
    }

    @Test
    public void testPidMintedResourceCreationWithNullArgument() throws IOException, FedoraException {
        final FedoraObject object = repo.createResource(null);
        Assert.assertTrue("A newly created object should exist at the path given by the createObject() call.",
                repo.exists(object.getPath()));
    }

    @Test
    public void testBasicPropertiesCreation() throws IOException, FedoraException {
        final String objectPath = getRandomUniqueId();
        final FedoraObject object = repo.createObject(objectPath);
        final String sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "identifier> 'test' . } ";
        object.updateProperties(sparqlUpdate);
        final Iterator<Triple> tripleIt = object.getProperties();
        while (tripleIt.hasNext()) {
            final Triple t = tripleIt.next();
            if (t.objectMatches(NodeFactory.createLiteral("test"))
                    && t.predicateMatches(NodeFactory.createURI(RdfLexicon.DC_NAMESPACE + "identifier"))) {
                return;
            }
        }
        Assert.fail("Unable to verify added object property!");
    }

    @Test
    public void testBasicDatastreamPropertiesCreation() throws IOException, FedoraException {
        final String objectPath = getRandomUniqueId();
        final FedoraObject object = repo.createObject(objectPath);
        final String datastreamPath = objectPath + "/" + getRandomUniqueId();
        final FedoraDatastream datastream = repo.createDatastream(datastreamPath, getStringTextContent("test"));
        final String sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "identifier> 'test' . } ";
        datastream.updateProperties(sparqlUpdate);
        final Iterator<Triple> tripleIt = datastream.getProperties();
        while (tripleIt.hasNext()) {
            final Triple t = tripleIt.next();
            if (t.objectMatches(NodeFactory.createLiteral("test"))
                    && t.predicateMatches(NodeFactory.createURI(RdfLexicon.DC_NAMESPACE + "identifier"))) {
                return;
            }
        }
        Assert.fail("Unable to verify added datastream property!");
    }

    @Test
    public void testCreateOrUpdateRedirectDatastream() throws FedoraException, IOException {
        final String objectPath = getRandomUniqueId();
        repo.createObject(objectPath);
        final String value = "Value of first datastream.";

        // create a text datastream with the value "test"
        final String datastreamPath1 = objectPath + "/" + getRandomUniqueId();
        final FedoraDatastream datastream1 = repo.createDatastream(datastreamPath1, getStringTextContent(value));

        // create a second datastream that is a redirect to the first
        final String datastreamPath2 = objectPath + "/" + getRandomUniqueId();
        final FedoraDatastream datastream2
                = repo.createOrUpdateRedirectDatastream(datastreamPath2, repo.getRepositoryUrl() + datastreamPath1);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(datastream2.getContent(), baos);
        Assert.assertEquals("Second datastream should be a redirect to the first!", value, baos.toString("UTF-8"));

    }

    @Test
    public void testMoveResource() throws FedoraException {
        final String originResourcePath = getRandomUniqueId();
        final String destinyResourcePath = getRandomUniqueId();

        // create origin resource
        FedoraObject originResource = repo.createObject(originResourcePath);
        Assert.assertNotNull(originResource);
        Assert.assertEquals(originResourcePath, originResource.getPath());

        // move resource to another location
        originResource.move(destinyResourcePath);
        final FedoraObject destinyResource = repo.getObject(destinyResourcePath);
        Assert.assertNotNull(destinyResource);

        // try to obtain, from origin, the object that was moved
        try {
            originResource = repo.getObject(originResourcePath);
            Assert.fail("An exception was expected but it didn't happened!");
        } catch (FedoraException e) {
            Assert.assertTrue(e.getMessage().contains("410 Gone"));
        }
    }

    @Test
    public void testForceMoveResource() throws FedoraException {
        final String originResourcePath = getRandomUniqueId();
        final String destinyResourcePath = getRandomUniqueId();

        // create origin resource
        FedoraObject originResource = repo.createObject(originResourcePath);
        Assert.assertNotNull(originResource);
        Assert.assertEquals(originResourcePath, originResource.getPath());

        // move resource to another location and remove tombstone
        originResource.forceMove(destinyResourcePath);
        final FedoraObject destinyResource = repo.getObject(destinyResourcePath);
        Assert.assertNotNull(destinyResource);

        // try to obtain, from origin, the object that was moved and doesn't have a tombstone because it was removed
        try {
            originResource = repo.getObject(originResourcePath);
            Assert.fail("An exception was expected but it didn't happened!");
        } catch (FedoraException e) {
            Assert.assertTrue(e.getClass() == NotFoundException.class);
        }
    }

    @Test
    public void testCopyResource() throws FedoraException {
        final String originResourcePath = getRandomUniqueId();
        final String originChildResourcePath = originResourcePath + "/" + getRandomUniqueId();
        final String destinyResourcePath = getRandomUniqueId();

        // create origin resource
        FedoraObject originResource = repo.createObject(originResourcePath);
        Assert.assertNotNull(originResource);
        Assert.assertEquals(originResourcePath, originResource.getPath());

        // create child origin resource
        final FedoraObject originChildResource = repo.createObject(originChildResourcePath);
        Assert.assertNotNull(originChildResource);
        Assert.assertEquals(originChildResourcePath, originChildResource.getPath());

        // copy resource to another location
        originResource.copy(destinyResourcePath);
        final FedoraObject destinyResource = repo.getObject(destinyResourcePath);
        Assert.assertNotNull(destinyResource);
        Assert.assertEquals(destinyResourcePath, destinyResource.getPath());

        // ensure that copied resource has the same number of child resources
        originResource = repo.getObject(originResourcePath);
        final Collection<FedoraResource> originChildren = originResource.getChildren(null);
        final Collection<FedoraResource> destinyChildren = destinyResource.getChildren(null);
        Assert.assertEquals(originChildren.size(),destinyChildren.size());
    }

    @Test
    public void testDeleteResource() throws FedoraException {
        final String resourcePath = getRandomUniqueId();
        // create resource
        FedoraObject resource = repo.createObject(resourcePath);
        Assert.assertNotNull(resource);
        Assert.assertEquals(resourcePath, resource.getPath());

        // delete resource
        resource.delete();

        try {
            resource = repo.getObject(resourcePath);
        } catch (FedoraException e) {
            Assert.assertTrue(e.getMessage().contains("410 Gone"));
        }
    }

    @Test
    public void testForceDeleteResource() throws FedoraException {
        final String resourcePath = getRandomUniqueId();
        // create resource
        FedoraObject resource = repo.createObject(resourcePath);
        Assert.assertNotNull(resource);
        Assert.assertEquals(resourcePath, resource.getPath());

        // delete resource and remove tombstone
        resource.forceDelete();

        try {
            resource = repo.getObject(resourcePath);
        } catch (FedoraException e) {
            Assert.assertTrue(e.getClass() == NotFoundException.class);
        }
    }

    private FedoraContent getStringTextContent(final String value) throws UnsupportedEncodingException {
        return new FedoraContent().setContent(new ByteArrayInputStream(value.getBytes("UTF-8")))
                .setContentType("text/plain");
    }

    private String getRandomUniqueId() {
        return UUID.randomUUID().toString();
    }

    @Test
    public void testListVersionsObject() throws FedoraException {
      final String path = getRandomUniqueId();
      repo.createObject(path);
      final FedoraObject object = repo.getObject(path);
      String sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "identifier> 'test' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V1");
      sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "title> 'title' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V2");
      final List<String> versions = object.getVersionsName();
      Assert.assertEquals(2, versions.size());
      Assert.assertTrue(versions.contains("V1"));
      Assert.assertTrue(versions.contains("V2"));
    }

    @Test
    public void testListVersionsDataStreams() throws FedoraException, IOException {
      final String objectPath = getRandomUniqueId();
      repo.createObject(objectPath);
      final FedoraObject object = repo.getObject(objectPath);
      final String datastreamPath = objectPath + "/" + getRandomUniqueId();
      final FedoraDatastream datastream = repo.createDatastream(datastreamPath, getStringTextContent("content V1"));
      datastream.createVersionSnapshot("V1Data");
      datastream.updateContent(getStringTextContent("content V2"));
      datastream.createVersionSnapshot("V2Data");
      final List<String> versions = datastream.getVersionsName();
      Assert.assertEquals(2, versions.size());
      Assert.assertTrue(versions.contains("V1Data"));
      Assert.assertTrue(versions.contains("V2Data"));
    }

    @Test
    public void testGetVersion() throws FedoraException {
      final String path = getRandomUniqueId();
      repo.createObject(path);
      final FedoraObject object = repo.getObject(path);
      String sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "identifier> 'test' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V1");
      sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "title> 'title' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V2");
      final FedoraObject objectV1 = repo.getObjectVersion(path,"V1");
      final FedoraObject objectV2 = repo.getObjectVersion(path,"V2");

      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "identifier", "test",objectV1.getProperties()));
      Assert.assertTrue(!containsProperty(RdfLexicon.DC_NAMESPACE + "title", "title",objectV1.getProperties()));
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "identifier", "test",objectV2.getProperties()));
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "title", "title",objectV2.getProperties()));
    }

    @Test
    public void testGetVersionDatastream() throws FedoraException, IOException {
      final String objectPath = getRandomUniqueId();
      repo.createObject(objectPath);
      final FedoraObject object = repo.getObject(objectPath);
      final String datastreamPath = objectPath + "/" + getRandomUniqueId();
      final FedoraDatastream datastream = repo.createDatastream(datastreamPath, getStringTextContent("content V1"));
      datastream.createVersionSnapshot("V1Data");
      datastream.updateContent(getStringTextContent("content V2"));
      datastream.createVersionSnapshot("V2Data");
      final FedoraDatastream dataV1 = repo.getDatastreamVersion(datastreamPath, "V1Data");
      final FedoraDatastream dataV2 = repo.getDatastreamVersion(datastreamPath, "V2Data");
      final String v1Content = IOUtils.toString(dataV1.getContent(), "UTF-8");
      final String v2Content = IOUtils.toString(dataV2.getContent(), "UTF-8");
      Assert.assertEquals("content V1", v1Content);
      Assert.assertEquals("content V2", v2Content);
    }

    /*
    @Test
    public void testRevertVersionObject() throws FedoraException, IOException {
      final String path = getRandomUniqueId();
      repo.createObject(path);
      final FedoraObject object = repo.getObject(path);
      String sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "identifier> 'test' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V1");
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "identifier", "test",object.getProperties()));
      Assert.assertTrue(!containsProperty(RdfLexicon.DC_NAMESPACE + "title", "title",object.getProperties()));
      sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "title> 'title' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V2");
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "identifier", "test",object.getProperties()));
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "title", "title",object.getProperties()));
      object.revertToVersion("V1");
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "identifier", "test",object.getProperties()));
      Assert.assertTrue(!containsProperty(RdfLexicon.DC_NAMESPACE + "title", "title",object.getProperties()));
    }
    */

    @Test
    public void testRevertVersionDatastream() throws FedoraException, IOException {
      final String objectPath = getRandomUniqueId();
      repo.createObject(objectPath);
      final FedoraObject object = repo.getObject(objectPath);
      final String datastreamPath = objectPath + "/" + getRandomUniqueId();
      final FedoraDatastream datastream = repo.createDatastream(datastreamPath, getStringTextContent("content V1"));
      datastream.createVersionSnapshot("V1Data");
      datastream.updateContent(getStringTextContent("content V2"));
      datastream.createVersionSnapshot("V2Data");
      datastream.updateContent(getStringTextContent("content V3"));
      datastream.createVersionSnapshot("V3Data");
      datastream.revertToVersion("V2Data");
      final String contentv2 = IOUtils.toString(datastream.getContent(), "UTF-8");
      datastream.revertToVersion("V1Data");
      final String contentv1 = IOUtils.toString(datastream.getContent(), "UTF-8");

      Assert.assertEquals("content V1", contentv1);
      Assert.assertEquals("content V2", contentv2);
    }

    @Test
    public void testDeleteVersionObject() throws FedoraException, IOException {
      final String path = getRandomUniqueId();
      repo.createObject(path);
      final FedoraObject object = repo.getObject(path);
      String sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "identifier> 'test' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V1");
      Assert.assertTrue(containsProperty(RdfLexicon.DC_NAMESPACE + "identifier", "test",object.getProperties()));
      Assert.assertTrue(!containsProperty(RdfLexicon.DC_NAMESPACE + "title", "title",object.getProperties()));
      sparqlUpdate = "INSERT DATA { <> <" + RdfLexicon.DC_NAMESPACE + "title> 'title' . } ";
      object.updateProperties(sparqlUpdate);
      object.createVersionSnapshot("V2");
      try {
        object.deleteVersion("V2");
        Assert.fail("When removing the last version, an exception should be thrown.");
      } catch (FedoraException fe) {
      }
      object.deleteVersion("V1");
      final List<String> versions = object.getVersionsName();
      Assert.assertTrue(versions.contains("V2"));
      Assert.assertTrue(!versions.contains("V1"));
    }

    @Test
    public void testDeleteVersionDatastream() throws FedoraException, IOException {
      final String objectPath = getRandomUniqueId();
      repo.createObject(objectPath);
      final FedoraObject object = repo.getObject(objectPath);
      final String datastreamPath = objectPath + "/" + getRandomUniqueId();
      final FedoraDatastream datastream = repo.createDatastream(datastreamPath, getStringTextContent("content V1"));
      datastream.createVersionSnapshot("V1Data");
      datastream.updateContent(getStringTextContent("content V2"));
      datastream.createVersionSnapshot("V2Data");
      datastream.updateContent(getStringTextContent("content V3"));
      datastream.createVersionSnapshot("V3Data");

      try {
        datastream.deleteVersion("V3Data");
        Assert.fail("When removing the last version, an exception should be thrown.");
      } catch (FedoraException fe) {
      }
      datastream.deleteVersion("V2Data");
      List<String> versions = datastream.getVersionsName();
      Assert.assertTrue(versions.contains("V1Data"));
      Assert.assertTrue(!versions.contains("V2Data"));
      Assert.assertTrue(versions.contains("V3Data"));
      datastream.deleteVersion("V1Data");
      versions = datastream.getVersionsName();
      Assert.assertTrue(!versions.contains("V1Data"));
      Assert.assertTrue(!versions.contains("V2Data"));
      Assert.assertTrue(versions.contains("V3Data"));
    }

    private boolean containsProperty(final String predicate, final String value, final Iterator<Triple> properties) {
      boolean contains = false;
      while (properties.hasNext()) {
        final Triple p = properties.next();
        if (p.getPredicate().getURI().equalsIgnoreCase(predicate)) {
          if (p.getObject().isLiteral()) {
            if (p.getObject().getLiteralValue().toString().equalsIgnoreCase(value)) {
              contains = true;
              break;
            }
          }
        }
      }
      return contains;
    }
}
