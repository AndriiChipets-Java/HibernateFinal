package org.anch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStringCommands;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.anch.dao.CityDao;
import org.anch.dao.CountryDao;
import org.anch.domain.City;
import org.anch.domain.Country;
import org.anch.domain.CountryLanguage;
import org.anch.redis.CityCountry;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {

    private final CityDao cityDao;
    private final CountryDao countryDao;
    private final ObjectMapper mapper;
    private final RedisClient redisClient;
    private final SessionFactory sessionFactory;

    @Transactional
    @Override
    public List<City> fetchData() {

        try (Session session = sessionFactory.getCurrentSession()) {
            List<City> allCities = new ArrayList<>();
            session.beginTransaction();

            /* The List<Country> countries are invoked to reduce the number of requests to DB to get List<City>
           from method cityDao.getItems(i, step) */
            List<Country> countries = countryDao.getAll();

            int totalCount = cityDao.getTotalCount();
            int step = 500;
            for (int i = 0; i < totalCount; i += step) {
                allCities.addAll(cityDao.getItems(i, step));
            }
            session.getTransaction().commit();
            return allCities;
        }
    }

    @Override
    public void pushToRedis(List<CityCountry> data) {

        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (CityCountry cityCountry : data) {
                try {
                    sync.set(String.valueOf(cityCountry.getId()), mapper.writeValueAsString(cityCountry));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void testRedisData(List<Integer> ids) {

        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            RedisStringCommands<String, String> sync = connection.sync();
            for (Integer id : ids) {
                String value = sync.get(String.valueOf(id));
                try {
                    mapper.readValue(value, CityCountry.class);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void testMysqlData(List<Integer> ids) {

        try (Session session = sessionFactory.getCurrentSession()) {
            session.beginTransaction();
            for (Integer id : ids) {
                City city = cityDao.getById(id);
                Set<CountryLanguage> languages = city.getCountry().getLanguages();
            }
            session.getTransaction().commit();
        }
    }
}
