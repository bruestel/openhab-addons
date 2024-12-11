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
package org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class DeviceDescription {
    public final List<Status> statusList;
    public final List<Setting> settingList;
    public final List<Event> eventList;
    public final List<Command> commandList;
    public final List<Option> optionList;
    public final List<Program> programList;
    public final Optional<Integer> activeProgramUid;
    public final Optional<Integer> selectedProgramUid;
    public final List<EnumerationType> enumerationTypeList;

    public DeviceDescription(List<Status> statusList, List<Setting> settingList, List<Event> eventList,
            List<Command> commandList, List<Option> optionList, List<Program> programList,
            Optional<Integer> activeProgramUid, Optional<Integer> selectedProgramUid,
            List<EnumerationType> enumerationTypeList) {
        this.statusList = List.copyOf(statusList);
        this.settingList = List.copyOf(settingList);
        this.eventList = List.copyOf(eventList);
        this.commandList = List.copyOf(commandList);
        this.optionList = List.copyOf(optionList);
        this.programList = List.copyOf(programList);
        this.activeProgramUid = activeProgramUid;
        this.selectedProgramUid = selectedProgramUid;
        this.enumerationTypeList = List.copyOf(enumerationTypeList);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", DeviceDescription.class.getSimpleName() + "[", "]")
                .add("statusList=" + statusList).add("settingList=" + settingList).add("eventList=" + eventList)
                .add("commandList=" + commandList).add("optionList=" + optionList).add("programList=" + programList)
                .add("activeProgramUid=" + activeProgramUid).add("selectedProgramUid=" + selectedProgramUid)
                .add("enumerationTypeList=" + enumerationTypeList).toString();
    }
}
