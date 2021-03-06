package me.magnet.consultant;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.Test;

public class RandomizedWeightedDistanceStrategyTest extends RoutingStrategyTest {

	public RandomizedWeightedDistanceStrategyTest() {
		super(RoutingStrategies.RANDOMIZED_WEIGHTED_DISTANCE);
	}

	@Test
	public void testThatAllServicesAreReturned() {
		ServiceLocator locations = strategy.locateInstances(serviceInstanceBackend, SERVICE_1);
		Set<ServiceInstance> instances = Sets.newHashSet(iterate(locations));
		assertEquals(Sets.newHashSet(dc1node1service1, dc1node2service1, dc1node3service1), instances);
	}

}
