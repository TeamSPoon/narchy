/*
 * Copyright (C) 2015 Machine Learning Lab - University of Trieste, 
 * Italy (http:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:
 */
package jcog.grammar.evolve.postprocessing;

import jcog.grammar.evolve.configuration.Configuration;
import jcog.grammar.evolve.outputs.Results;

import java.util.Map;

/**
 * Classes which implements this interface have to call the BestSelector in order to
 * select the best solution. Populate the Results instance file with requested stats and save
 * the results and/or persist results on file (for example).
 * @author MaleLabTs
 */
public interface Postprocessor {

    void setup(Map<String, String> parameters);

    void elaborate(Configuration config, Results results, long timeTaken);
}
