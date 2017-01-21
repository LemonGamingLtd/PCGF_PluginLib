/*
 *   Copyright (C) 2016 GeorgH93
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package at.pcgamingfreaks;

import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Version
{
	public static final String VERSION_STING_FORMAT = "[vV]?\\d+(\\.\\d+)*(-[^-\\s]+)*";
	private static final int SAME = 0, OLDER = -1, NEWER = 1;
	private static final String PRE_RELEASE_TAG_FORMAT = "\\w+\\d";
	private static final String[] PRE_RELEASE_TAGS = new String[] { "alpha", "beta", "pre", "rc", "snapshot"};
	private static final Map<String, Integer> PRE_RELEASE_TAG_VALUE_RESOLUTION = new ConcurrentHashMap<>();

	static
	{
		for(int i = 0; i < PRE_RELEASE_TAGS.length; i++)
		{
			PRE_RELEASE_TAG_VALUE_RESOLUTION.put(PRE_RELEASE_TAGS[i], (PRE_RELEASE_TAGS.length + 1 - i) * 10);
		}
	}

	private final String rawVersion;
	@SuppressWarnings("FieldCanBeLocal")
	private final String[] optionalTags;
	private final int[] version;
	private final int hashCode;

	/**
	 * @param version A string representing this version. Must be in the format: {@value #VERSION_STING_FORMAT}
	 * @throws InvalidVersionStringException The exception is thrown when the given string doesn't match the required format.
	 */
	public Version(@NotNull String version) throws InvalidVersionStringException
	{
		this(version, false);
	}

	/**
	 * @param version A string representing this version. Must be in the format: {@value #VERSION_STING_FORMAT}
	 * @param ignoreOptionalTags Ignores tags like -alpha for the version comparison.
	 * @throws InvalidVersionStringException The exception is thrown when the given string doesn't match the required format.
	 */
	public Version(@NotNull String version, boolean ignoreOptionalTags) throws InvalidVersionStringException
	{
		//noinspection ConstantConditions
		if(version == null || version.isEmpty() || !isValidVersionString(version)) throw new InvalidVersionStringException("The version string must be in the format: " + VERSION_STING_FORMAT);
		if(version.startsWith("v") || version.startsWith("V")) version = version.substring(1);
		this.rawVersion = version;
		// Prepare data
		String[] comps = version.split("-", 2);
		version = comps[0].replaceAll("(\\.0)*$", "");
		String options = comps.length > 1 ? comps[1] : "";
		this.optionalTags = options.split("-");
		comps = version.split("\\.");
		List<String> tags = (!ignoreOptionalTags) ? getAll(this.optionalTags, PRE_RELEASE_TAGS) : null;
		boolean notAFinalVersion = !ignoreOptionalTags && tags.size() > 0;
		this.version = new int[notAFinalVersion ? comps.length + 1 : comps.length];
		for(int i = 0; i < comps.length; i++)
		{
			this.version[i] = Integer.parseInt(comps[i]);
		}
		if(notAFinalVersion)
		{
			int last = 0;
			for(String str : tags)
			{
				if(last == 0) last = Integer.MAX_VALUE;
				int preReleaseTagNumber = 0;
				String tag = str.toLowerCase();
				if(str.matches(PRE_RELEASE_TAG_FORMAT))
				{
					preReleaseTagNumber = Integer.parseInt(tag.substring(tag.length() - 1));
					tag = tag.substring(0, tag.length() - 1);
				}
				last = (last - PRE_RELEASE_TAG_VALUE_RESOLUTION.get(tag)) + preReleaseTagNumber;
			}
			this.version[this.version.length - 1] = last;
			if(last > 0)
			{
				for(int i = this.version.length - 2; i >= 0; i--)
				{
					if(this.version[i] > 0 || i == 0)
					{
						this.version[i]--;
						break;
					}
				}
			}
		}
		this.hashCode = Arrays.hashCode(this.version);
	}

	private static List<String> getAll(String[] source, String[] searchForArray)
	{
		List<String> result = new LinkedList<>();
		for(String searchFor : searchForArray)
		{
			result.addAll(StringUtils.getAllContainingIgnoreCase(source, searchFor));
		}
		return result;
	}

	/**
	 * Checks if the given version string matches the required format.
	 *
	 * @param version The String to check.
	 * @return True if the string matches the format. False if not.
	 */
	public static boolean isValidVersionString(String version)
	{
		return version.matches(VERSION_STING_FORMAT);
	}

	/**
	 * Compares two version with each other.
	 *
	 * @param otherVersion The version to compare with.
	 * @return -1 this older than otherVersion, 0 equals, 1 this newer than otherVersion
	 */
	private byte compare(@NotNull Version otherVersion)
	{
		Validate.notNull(otherVersion);
		int c = Math.min(this.version.length, otherVersion.version.length);
		for(int i = 0; i < c; i++)
		{
			if(otherVersion.version[i] > this.version[i])
			{
				return OLDER;
			}
			else if(otherVersion.version[i] < this.version[i])
			{
				return NEWER;
			}
		}
		// If both version are the same for the length of the shorter version the version that has more digits probably is the newer one.
		if(this.version.length == otherVersion.version.length) return 0;
		boolean otherLonger = otherVersion.version.length > this.version.length;
		int[] longer = (otherLonger) ? otherVersion.version : this.version;
		byte result = SAME;
		for(int i = c; i < longer.length; i++)
		{
			if(longer[i] > 0)
			{
				result = (byte) ((otherLonger) ? OLDER : NEWER);
			}
		}
		return result;
	}

	//region comparision functions
	/**
	 * Checks if the version is newer than the given version.
	 *
	 * @param otherVersion The version to compare with.
	 * @return True if the version is newer, false if not.
	 */
	public boolean newerThan(@NotNull Version otherVersion)
	{
		return compare(otherVersion) == NEWER;
	}

	/**
	 * Checks if the version is newer or the same than the given version.
	 *
	 * @param otherVersion The version to compare with.
	 * @return True if the version is newer or the same, false if not.
	 */
	public boolean newerOrEqualThan(@NotNull Version otherVersion)
	{
		return compare(otherVersion)>= SAME;
	}

	/**
	 * Checks if the version is older than the given version.
	 *
	 * @param otherVersion The version to compare with.
	 * @return True if the version is older, false if not.
	 */
	public boolean olderThan(@NotNull Version otherVersion)
	{
		return compare(otherVersion) == OLDER;
	}

	/**
	 * Checks if the version is older or the same than the given version.
	 *
	 * @param otherVersion The version to compare with.
	 * @return True if the version is older or the same, false if not.
	 */
	public boolean olderOrEqualThan(@NotNull Version otherVersion)
	{
		return compare(otherVersion) <= SAME;
	}
	//endregion

	//region Overriding functions
	/**
	 * Returns the original version string. But without the optional "v" at the start.
	 *
	 * @return The string representing this version.
	 */
	@Override
	public String toString()
	{
		return this.rawVersion;
	}

	/**
	 * Compares two versions if they are the same.
	 *
	 * @param otherVersion The version to compare with.
	 * @return True if both versions are equal, false if not.
	 */
	@Override
	public boolean equals(Object otherVersion)
	{
		return otherVersion instanceof Version && compare((Version) otherVersion) == SAME;
	}

	@Override
	public int hashCode()
	{
		return hashCode;
	}
	//endregion

	/**
	 * This exception is thrown when the string representing an version is invalid.
	 */
	public static class InvalidVersionStringException extends IllegalArgumentException
	{
		public InvalidVersionStringException(String string)
		{
			super(string);
		}
	}
}