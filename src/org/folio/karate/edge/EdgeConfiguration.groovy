package org.folio.karate.edge

class EdgeConfiguration {

    List<EdgeService> edgeServices = []

    EdgeConfiguration(def jsonContents) {
        jsonContents.each { entry ->
            EdgeService edgeService = new EdgeService(entry)
            edgeServices.add(edgeService)
        }
    }

}
