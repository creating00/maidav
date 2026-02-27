package com.sales.maidav.service.client;

import com.sales.maidav.model.client.Client;
import java.util.List;

public interface ClientService {

    List<Client> findAll();

    List<Client> search(String term);

    Client findById(Long id);

    Client create(Client client);

    Client update(Long id, Client client);

    void delete(Long id);

    long count();
}

