package org.kurento.room.test;

/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 * 
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

/**
 * Room demo integration test (basic version).
 *
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @since 5.0.0
 */
public class SeqAddRemoveUser extends RoomTest {

	private static final int WAIT_TIME = 500;

	private static final int PLAY_TIME = 5; // seconds

	private static final int NUM_USERS = 4;

	private static final int NUM_ITERATIONS = 2;

	@Test
	public void nUsersRoomTest() throws InterruptedException,
			ExecutionException, TimeoutException {

		boolean[] activeUsers = new boolean[NUM_USERS];

		browsers = createBrowsers(NUM_USERS);

		for (int cycle = 0; cycle < NUM_ITERATIONS; cycle++) {

			for (int i = 0; i < NUM_USERS; i++) {
				String userName = "user" + i;
				log.info("User '{}' joining room '{}'", userName, roomName);
				joinToRoom(browsers.get(i), userName, roomName);
				activeUsers[i] = true;
				sleep(WAIT_TIME);
				verify(browsers, activeUsers);
				log.info("User '{}' joined to room '{}'", userName, roomName);
			}

			for (int i = 0; i < NUM_USERS; i++) {
				for (int j = 0; j < NUM_USERS; j++) {
					String userName = "user" + i;
					waitForStream(userName, browsers.get(i),
							"native-video-user" + j + "_webcam");
					log.debug("Received media from user" + j
							+ " in browser of user" + i);
				}
			}

			// Guard time to see application in action
			Thread.sleep(PLAY_TIME * 1000);

			// Stop application by caller
			for (int i = 0; i < NUM_USERS; i++) {
				String userName = "user" + i;
				log.info("User '{}' is exiting from room '{}'", userName,
						roomName);
				exitFromRoom(userName, browsers.get(i));
				activeUsers[i] = false;
				sleep(WAIT_TIME);
				verify(browsers, activeUsers);
				log.info("User '{}' exited from room '{}'", userName, roomName);
			}
		}
	}

}
