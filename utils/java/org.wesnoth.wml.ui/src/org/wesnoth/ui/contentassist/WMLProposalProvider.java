/*******************************************************************************
 * Copyright (c) 2010 by Timotei Dolean <timotei21@gmail.com>
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.wesnoth.ui.contentassist;

import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.parsetree.CompositeNode;
import org.eclipse.xtext.parsetree.LeafNode;
import org.eclipse.xtext.parsetree.NodeUtil;
import org.eclipse.xtext.ui.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ui.editor.contentassist.ICompletionProposalAcceptor;
import org.wesnoth.Logger;
import org.wesnoth.preprocessor.Define;
import org.wesnoth.schema.SchemaParser;
import org.wesnoth.schema.Tag;
import org.wesnoth.schema.TagKey;
import org.wesnoth.ui.WMLUiModule;
import org.wesnoth.ui.WMLUtil;
import org.wesnoth.ui.labeling.WMLLabelProvider;
import org.wesnoth.utils.ProjectUtils;
import org.wesnoth.wML.WMLKey;
import org.wesnoth.wML.WMLTag;
import org.wesnoth.wML.impl.WMLFactoryImpl;
import org.wesnoth.wml.core.ConfigFile;


@SuppressWarnings("unused")
public class WMLProposalProvider extends AbstractWMLProposalProvider
{
	/**
	 * We have the following priorities:
	 *
	 * 1700 - key values
	 * 1500 - key names
	 * 1000 - tags
	 *  100 - macro calls
	 */

	public WMLProposalProvider()
	{
		super();
		// load the schema so we know what to suggest for autocomplete
		SchemaParser.getInstance().parseSchema(false);
	}

	@Override
	public void completeWMLKey_Name(EObject model, Assignment assignment,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		super.completeWMLKey_Name(model, assignment, context, acceptor);
		dbg("completing wmlkeyname");
		addKeyNameProposals(model, context, acceptor);
	}

	@Override
	public void complete_WMLKeyValue(EObject model, RuleCall ruleCall,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		super.complete_WMLKeyValue(model, ruleCall, context, acceptor);
		dbg("completing wmlkeyvalue - rule");
		addKeyValueProposals(model, context, acceptor);
	}

	@Override
	public void complete_WMLTag(EObject model, RuleCall ruleCall,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		super.complete_WMLTag(model, ruleCall, context, acceptor);
		dbg("completing wmltag - rule");
		addTagProposals(model, true, context, acceptor);
	}

	@Override
	public void completeWMLTag_Name(EObject model, Assignment assignment,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		super.completeWMLTag_Name(model, assignment, context, acceptor);
		dbg("completing wmltagname");
		addTagProposals(model, false, context, acceptor);
	}

	@Override
	public void completeWMLMacroCall_Name(EObject model, Assignment assignment,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		super.completeWMLMacroCall_Name(model, assignment, context, acceptor);
		dbg("completing wmlmacrocallname");
		addMacroCallProposals(model, false, context, acceptor);
	}

	@Override
	public void complete_WMLMacroCall(EObject model, RuleCall ruleCall,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		super.complete_WMLMacroCall(model, ruleCall, context, acceptor);
		dbg("completing wmlmacrocall - rule");
		addMacroCallProposals(model, true, context, acceptor);
	}

	private void addMacroCallProposals(EObject model, boolean ruleProposal,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		IFile file = WMLUtil.getActiveEditorFile();
		if (file == null)
		{
			Logger.getInstance().logError("FATAL! file is null (and it shouldn't)");
			return;
		}

		for(Entry<String, Define> define : ProjectUtils.getCacheForProject(
								file.getProject()).getDefines().entrySet())
		{
			StringBuilder proposal = new StringBuilder(10);
			if (ruleProposal == true)
				proposal.append("{");
			proposal.append(define.getKey());

			for(String arg : define.getValue().getArguments())
				proposal.append(" " + arg);
			proposal.append("}");

			acceptor.accept(createCompletionProposal(proposal.toString(), define.getKey(),
					WMLLabelProvider.getImageByName("macrocall.png"), context, 100));
		}
	}

	/**
	 * Adss the wml key's names proposals
	 * @param model
	 * @param context
	 * @param acceptor
	 */
	private void addKeyValueProposals(EObject model,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		if (model != null && model instanceof WMLKey)
		{
			dbg(model);
			WMLKey key = (WMLKey)model;

			// handle the next_scenario and first_scenario
			if (key.getName().equals("next_scenario") ||
				key.getName().equals("first_scenario"))
			{
				IFile file = WMLUtil.getActiveEditorFile();
				if (file == null)
				{
					Logger.getInstance().logError("FATAL! file is null (and it shouldn't)");
					return;
				}

				for(ConfigFile config : ProjectUtils.
						getCacheForProject(file.getProject()).getConfigs().values())
				{
					if (config.getScenarioId() == null ||
						config.getScenarioId().isEmpty())
						continue;
					acceptor.accept(createCompletionProposal(config.getScenarioId(),
							config.getScenarioId(), WMLLabelProvider.getImageByName("scenario.png"),
							context, 1700));
				}
			}
			else
			{
				if (model.eContainer() != null &&
					model.eContainer() instanceof WMLTag)
				{
					WMLTag parent = (WMLTag) model.eContainer();
					Tag tag = SchemaParser.getInstance().getTags().get(parent.getName());
					if (tag != null)
					{
						TagKey tagKey = tag.getChildKey(key.getName());
						if (tagKey.isEnum())
						{
							String[] values = tagKey.getValue().split(",");
							for(String val : values)
							{
								acceptor.accept(createCompletionProposal(val, context, 1700));
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Adss the wml key's names proposals
	 * @param model
	 * @param context
	 * @param acceptor
	 */
	private void addKeyNameProposals(EObject model,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		WMLTag tag = null;
		if (model instanceof WMLTag)
			tag = (WMLTag)model;
		else if (model.eContainer() instanceof WMLTag)
			tag = (WMLTag)model.eContainer();

		if (tag != null)
		{
			if (SchemaParser.getInstance().	getTags().get(tag.getName()) != null)
			{
				boolean found = false;
				for(TagKey key : SchemaParser.getInstance().
						getTags().get(tag.getName()).getKeyChildren())
				{
					// skip forbidden keys
					if (key.isForbidden())
						continue;

					found = false;
					// check only non-repeatable keys
					if (key.isRepeatable() == false)
					{
						// don't suggest already completed keys
						for(WMLKey eKey: tag.getKeys())
							if (eKey.getName().equals(key.getName()))
								found = true;
					}

					if (found == false)
						acceptor.accept(createCompletionProposal(key.getName() + "=",
							key.getName(),
							getImage(WMLFactoryImpl.eINSTANCE.getWMLPackage().getWMLKey()),
							context, 1500));
				}
			}
		}
	}

	/**
	 * Adds the tag proposals
	 * @param model
	 * @param ruleProposal
	 * @param context
	 * @param acceptor
	 */
	private void addTagProposals(EObject model, boolean ruleProposal,
			ContentAssistContext context, ICompletionProposalAcceptor acceptor)
	{
		WMLTag parentTag = null;
		if (model instanceof WMLTag)
			parentTag = (WMLTag)model;
		else if (model.eContainer() instanceof WMLTag)
			parentTag = (WMLTag)model.eContainer();

		if (parentTag != null)
		{
			CompositeNode node = NodeUtil.getNode(model);

			String parentIndent = "";
			if (context.getCurrentNode().getOffset() > 0)
				parentIndent = ((LeafNode)NodeUtil.findLeafNodeAtOffset(node.getParent(),
						context.getCurrentNode().getOffset() -
						// if we have a non-rule proposal, subtract 1
						(ruleProposal ? 0 : 1)
						)).getText();

			// remove ugly new lines that break indentation
			parentIndent =  parentIndent.replace("\r", "").replace("\n", "");

			Tag tagChildren = SchemaParser.getInstance().getTags().get(parentTag.getName());
			if (tagChildren != null)
			{
				boolean found = false;
				for(Tag tag : tagChildren.getTagChildren())
				{
					// skip forbidden tags
					if (tag.isForbidden())
						continue;

					found = false;

					// check only non-repeatable tags
					if (tag.isRepeatable() == false)
					{
						for(WMLTag wmlTag : parentTag.getTags())
							if (wmlTag.getName().equals(tag.getName()))
							{
								found = true;
								break;
							}
					}

					if (found == false)
						acceptor.accept(tagProposal(tag, parentIndent,
								ruleProposal, context));
				}
			}
			else
				dbg("!!! no tag found with that name:" + parentTag.getName());
		}
		else // we are at the root
		{
			Tag rootTag = SchemaParser.getInstance().getTags().get("root");
			dbg("root node. adding tags: "+ rootTag.getTagChildren().size());
			for(Tag tag : rootTag.getTagChildren())
			{
				acceptor.accept(tagProposal(tag, "", ruleProposal, context));
			}
		}
	}

	/**
	 * Returns the proposal for the specified tag, usign the specified indent
	 * @param tag The tag from which to construct the proposal
	 * @param indent The indent used to indent the tag and subsequent keys
	 * @param ruleProposal Whether this is a proposal for an entire rule or not
	 * @param context
	 * @return
	 */
	private ICompletionProposal tagProposal(Tag tag, String indent, boolean ruleProposal,
					ContentAssistContext context)
	{
//		dbg("indent:[" + indent +"]");
		StringBuilder proposal = new StringBuilder();
		if (ruleProposal)
			proposal.append("[");
		proposal.append(tag.getName());
		proposal.append("]\n");
		for(TagKey key : tag.getKeyChildren())
		{
			if (key.isRequired())
				proposal.append(String.format("\t%s%s=\n",
						indent, key.getName()));
		}
		proposal.append(String.format("%s[/%s]",indent, tag.getName()));
		return createCompletionProposal(proposal.toString(), tag.getName(),
					getImage(WMLFactoryImpl.eINSTANCE.getWMLPackage().getWMLTag()),
					context, 100);
	}

	private ICompletionProposal createCompletionProposal(String proposal,
			ContentAssistContext context, int priority)
	{
		return createCompletionProposal(proposal, null, null, priority,
				context.getPrefix(), context);
	}

	private ICompletionProposal createCompletionProposal(String proposal, String displayString,
					ContentAssistContext context)
	{
		return createCompletionProposal(proposal, displayString, null, context);
	}

	public ICompletionProposal createCompletionProposal(String proposal, String displayString, Image image,
			ContentAssistContext contentAssistContext, int priority)
	{
		return createCompletionProposal(proposal, new StyledString(displayString),
					image, priority, contentAssistContext.getPrefix(), contentAssistContext);
	}
	/**
	 * Method for debugging the auto completion
	 * @param str
	 */
	private void dbg(Object str)
	{
		if (!(WMLUiModule.DEBUG))
			return;
		System.out.println(str.toString());
	}
}
