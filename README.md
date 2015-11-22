
# Presentation cheat sheet

    # spin up lattice box and target local cell
    vagrant destroy --force && vagrant up
    ltc target local.lattice.cf
    
    # build and start config server container
    ltc build-droplet config-server java -p config-server/build/libs/config-server-0.0.1-SNAPSHOT.jar
    ltc launch-droplet config-server config-server -m 512 -p 8888

    # build and start eureka server container
    ltc build-droplet eureka-server java -p eureka-server/build/libs/eureka-server-0.0.1-SNAPSHOT.jar
    ltc launch-droplet eureka-server eureka-server -m 512 -p 8761 -e CONFIG_SERVER_URL=http://config-server.local.lattice.cf

    # build and launch backend service
    ltc build-droplet committed-service java -p committed-service/build/libs/committed-service-0.0.1-SNAPSHOT.jar
    ltc launch-droplet committed-service committed-service -m 512 -p 8080 -e CONFIG_SERVER_URL=http://config-server.local.lattice.cf -e EUREKA_SERVER_URI=http://eureka-server.local.lattice.cf
    
