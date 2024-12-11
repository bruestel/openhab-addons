/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.homeconnectdirect.internal.service.profile.converter;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.Access;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.Handling;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.Level;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.Command;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.DeviceDescription;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.EnumerationType;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.Event;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.Option;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.Program;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.Setting;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.Status;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class DeviceDescriptionConverter implements Converter {

    @Override
    @SuppressWarnings("rawtypes")
    public boolean canConvert(Class type) {
        return type.equals(DeviceDescription.class);
    }

    @Override
    public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter,
            MarshallingContext marshallingContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {

        List<Status> statusList = new ArrayList<>();
        List<Setting> settingList = new ArrayList<>();
        List<Event> eventList = new ArrayList<>();
        List<Command> commandList = new ArrayList<>();
        List<Option> optionList = new ArrayList<>();
        List<Program> programList = new ArrayList<>();
        List<Program> activeProgramList = new ArrayList<>();
        List<Program> selectedProgramList = new ArrayList<>();
        List<EnumerationType> enumerationTypeList = new ArrayList<>();

        read(reader, statusList, settingList, eventList, commandList, optionList, programList, activeProgramList,
                selectedProgramList, enumerationTypeList);

        var device = new DeviceDescription(statusList, settingList, eventList, commandList, optionList, programList,
                activeProgramList.stream().findFirst().map(program -> program.uid),
                selectedProgramList.stream().findFirst().map(program -> program.uid), enumerationTypeList);

        return device;
    }

    private void read(HierarchicalStreamReader reader, List<Status> statusList, List<Setting> settingList,
            List<Event> eventList, List<Command> commandList, List<Option> optionList, List<Program> programList,
            List<Program> activeProgramList, List<Program> selectedProgramList,
            List<EnumerationType> enumerationTypeList) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String nodeName = reader.getNodeName();

            if ("status".equals(nodeName)) {
                var access = mapAccess(reader.getAttribute("access"));
                var available = Boolean.parseBoolean(reader.getAttribute("available"));
                var uid = mapHexIdString(reader.getAttribute("uid"));
                var enumerationType = mapHexIdStringAllowNull(reader.getAttribute("enumerationType"));
                var max = mapInteger(reader.getAttribute("max"));
                var min = mapInteger(reader.getAttribute("min"));
                var stepSize = mapInteger(reader.getAttribute("stepSize"));

                statusList.add(new Status(uid, access, available, min, max, stepSize, enumerationType));
            } else if ("statusList".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("setting".equals(nodeName)) {
                var access = mapAccess(reader.getAttribute("access"));
                var available = Boolean.parseBoolean(reader.getAttribute("available"));
                var uid = mapHexIdString(reader.getAttribute("uid"));
                var enumerationType = mapHexIdStringAllowNull(reader.getAttribute("enumerationType"));
                var max = mapInteger(reader.getAttribute("max"));
                var min = mapInteger(reader.getAttribute("min"));
                var stepSize = mapInteger(reader.getAttribute("stepSize"));

                settingList.add(new Setting(uid, access, available, min, max, stepSize, enumerationType));
            } else if ("settingList".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("event".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("uid"));
                var enumerationType = mapHexIdStringAllowNull(reader.getAttribute("enumerationType"));
                var level = mapLevel(reader.getAttribute("level"));
                var handling = mapHandling(reader.getAttribute("handling"));

                eventList.add(new Event(uid, level, handling, enumerationType));
            } else if ("eventList".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("command".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("uid"));
                var access = mapAccess(reader.getAttribute("access"));
                var available = Boolean.parseBoolean(reader.getAttribute("available"));

                commandList.add(new Command(uid, access, available));
            } else if ("commandList".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("option".equals(nodeName) && StringUtils.isNoneBlank(reader.getAttribute("uid"))) {
                var uid = mapHexIdString(reader.getAttribute("uid"));
                var access = mapAccess(reader.getAttribute("access"));
                var available = Boolean.parseBoolean(reader.getAttribute("available"));
                var enumerationType = mapHexIdStringAllowNull(reader.getAttribute("enumerationType"));
                var max = mapInteger(reader.getAttribute("max"));
                var min = mapInteger(reader.getAttribute("min"));
                var stepSize = mapInteger(reader.getAttribute("stepSize"));

                optionList.add(new Option(uid, access, available, min, max, stepSize, enumerationType));
            } else if ("optionList".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("programGroup".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("program".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("uid"));
                var available = Boolean.parseBoolean(reader.getAttribute("available"));
                var optionUidList = new ArrayList<Integer>();
                readProgramOptions(reader, optionUidList);

                programList.add(new Program(uid, available, optionUidList));
            } else if ("activeProgram".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("uid"));
                activeProgramList.add(new Program(uid, false, List.of()));
            } else if ("selectedProgram".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("uid"));
                selectedProgramList.add(new Program(uid, false, List.of()));
            } else if ("enumerationTypeList".equals(nodeName)) {
                read(reader, statusList, settingList, eventList, commandList, optionList, programList,
                        activeProgramList, selectedProgramList, enumerationTypeList);
            } else if ("enumerationType".equals(nodeName)) {
                var id = mapHexIdString(reader.getAttribute("enid"));
                var subsetOf = mapHexIdStringAllowNull(reader.getAttribute("subsetOf"));
                var valueList = new ArrayList<Integer>();
                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    if ("enumeration".equals(reader.getNodeName())) {
                        var value = mapInteger(reader.getAttribute("value"));
                        valueList.add(value);
                    }
                    reader.moveUp();
                }
                enumerationTypeList.add(new EnumerationType(id, subsetOf, valueList));
            }

            reader.moveUp();
        }
    }

    private void readProgramOptions(HierarchicalStreamReader reader, List<Integer> optionList) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String nodeName = reader.getNodeName();
            if ("option".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("refUID"));
                optionList.add(uid);
            }
            reader.moveUp();
        }
    }

    private Access mapAccess(@Nullable String access) {
        if (access == null) {
            return Access.NONE;
        }

        return switch (access) {
            case "read" -> Access.READ;
            case "readWrite" -> Access.READ_WRITE;
            case "writeOnly" -> Access.WRITE_ONLY;
            default -> Access.NONE;
        };
    }

    private Handling mapHandling(@Nullable String handling) {
        if (handling == null) {
            return Handling.NONE;
        }

        return switch (handling) {
            case "acknowledge" -> Handling.ACKNOWLEDGE;
            case "decision" -> Handling.DECISION;
            default -> Handling.NONE;
        };
    }

    private Level mapLevel(@Nullable String level) {
        if (level == null) {
            return Level.HINT;
        }

        return switch (level) {
            case "info" -> Level.INFO;
            case "alert" -> Level.ALERT;
            case "critical" -> Level.CRITICAL;
            case "warning" -> Level.WARNING;
            default -> Level.HINT;
        };
    }

    private @Nullable Integer mapInteger(@Nullable String integer) {
        if (integer == null) {
            return null;
        }

        try {
            return Integer.parseInt(integer);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @Nullable Integer mapHexIdStringAllowNull(@Nullable String hexIdString) {
        if (hexIdString == null) {
            return null;
        }

        try {
            return Integer.parseInt(hexIdString, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int mapHexIdString(String hexIdString) {
        try {
            return Integer.parseInt(hexIdString, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
