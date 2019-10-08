package com.virjar.sekiro.api;

public interface SekiroRequestHandler {
    void handleRequest(SekiroRequest sekiroRequest, SekiroResponse sekiroResponse);
}
