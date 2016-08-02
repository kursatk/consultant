package me.magnet.consultant;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class RoutingStrategies {

	public static final RoutingStrategy NETWORK_DISTANCE = (locator, serviceName) ->
			new ServiceLocations(() -> locator.listInstances(serviceName).iterator(), () -> {
				List<String> datacenters = locator.listDatacenters();
				Collections.reverse(datacenters);

				ServiceLocations current = null;

				for (String datacenter : datacenters) {
					boolean sameDatacenter = locator.getDatacenter()
							.map(datacenter::equals)
							.orElse(false);

					if (sameDatacenter) {
						continue;
					}

					if (current == null) {
						current = new ServiceLocations(() -> locator.listInstances(serviceName, datacenter).iterator());
					}
					else {
						current = new ServiceLocations(() -> locator.listInstances(serviceName, datacenter).iterator(), current);
					}
				}

				return current;
			});

	public static RoutingStrategy randomizedWeightedDistance(double threshold) {
		return new RoutingStrategy() {

			private final Random random = new Random();

			@Override
			public ServiceLocations locateInstances(ServiceLocator serviceLocator, String serviceName) {
				return NETWORK_DISTANCE.locateInstances(serviceLocator, serviceName)
						.map(iterator -> {
							List<ServiceInstance> instances = Lists.newArrayList(iterator);
							if (instances.size() <= 1) {
								// No routing to do.
								return instances.iterator();
							}

							/*
							 * Pick the index of the first instance to try based on a random chance (0..1 value).
							 * If the chance was < 0.5 then use the instance on index 0.
							 * If the chance was >= 0.5 and < 0.75 then use the instance on index 1.
							 * If the chance was >= 0.75 and < 0.875 then use the instance on index 2.
							 * If the chance was >= 0.875 and < 0.9375 then use the instance on index 3.
							 * Etc...
							 */
							int index = 0;
							while (random.nextDouble() < threshold && index < instances.size() - 1) {
								index++;
							}

							Collections.rotate(instances, index);
							return instances.iterator();
						});
			}
		};
	}

	public static final RoutingStrategy RANDOMIZED_WEIGHTED_DISTANCE = randomizedWeightedDistance(0.5);

	public static final RoutingStrategy ROUND_ROBIN = new RoutingStrategy() {

		private final Map<String, ServiceInstance> lastRequested = Maps.newConcurrentMap();

		@Override
		public ServiceLocations locateInstances(ServiceLocator serviceLocator, String serviceName) {
			return NETWORK_DISTANCE.locateInstances(serviceLocator, serviceName)
					.map(iterator -> {
						// Ensure the instances are always sorted the same way.
						List<ServiceInstance> instances = Lists.newArrayList(iterator);
						Comparator<ServiceInstance> comparing = Comparator.comparing(entry -> entry.getNode().getNode());
						comparing = comparing.thenComparing(instance -> instance.getService().getId());
						Collections.sort(instances, comparing);

						Set<ServiceInstance> attempted = Sets.newHashSet();
						return new Iterator<ServiceInstance>() {

							@Override
							public boolean hasNext() {
								return attempted.size() < instances.size();
							}

							@Override
							public ServiceInstance next() {
								int start = 0;
								ServiceInstance lastInstanceUsed = lastRequested.get(serviceName);
								if (lastInstanceUsed != null) {
									start = instances.indexOf(lastInstanceUsed) + 1;
								}

								for (int i = start; i < start + instances.size(); i++) {
									ServiceInstance instance = instances.get(i % instances.size());
									if (!attempted.add(instance)) {
										continue;
									}
									lastRequested.put(serviceName, instance);
									return instance;
								}
								throw new NoSuchElementException();
							}

						};
					})
					.setListener(taken -> lastRequested.put(serviceName, taken));
		}

		@Override
		public void reset() {
			lastRequested.clear();
		}

	};

	public static final RoutingStrategy RANDOMIZED = (serviceLocator, serviceName) ->
			NETWORK_DISTANCE.locateInstances(serviceLocator, serviceName)
					.map(iterator -> {
						List<ServiceInstance> instances = Lists.newArrayList(iterator);
						Collections.shuffle(instances);
						return instances.iterator();
					});


	private RoutingStrategies() {
		// Prevent instantiation.
	}

}
